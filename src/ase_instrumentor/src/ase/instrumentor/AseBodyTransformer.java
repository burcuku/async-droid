package ase.instrumentor;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.JIdentityStmt;
import soot.options.Options;

public class AseBodyTransformer extends BodyTransformer {

    private static SootClass aseTestBridgeClass;
    private static SootMethod initiateScheduler, waitMyTurn, notifyScheduler, enterMonitor, exitMonitor, incNumUIBlocks, decNumUIBlocks;
    private static SootMethod setActivityViewTraverser, setFragmentViewTraverser, setActionBarMenu, setRecorderForActionBar;

    private static SootClass activityClass;

    public static void main(String[] args) {
        // args[0]: directory from which to process classes
        // args[1]: path for finding the android.jar file

        PackManager.v().getPack("jtp").add(
            new Transform("jtp.myInstrumenter", new AseBodyTransformer()));

        soot.Main.main(new String[]{
            "-debug",
            "-prepend-classpath",
            "-process-dir", args[0],
            "-android-jars", args[1],
            "-src-prec", "apk",
            "-output-format", "dex",
            "-allow-phantom-refs"
        });
    }


    private void init() {
        if (aseTestBridgeClass != null)
            return;

        aseTestBridgeClass = Scene.v().getSootClass("ase.AseTestBridge");
        initiateScheduler = aseTestBridgeClass.getMethod("void initiateScheduler(android.app.Activity)");
        waitMyTurn = aseTestBridgeClass.getMethod("void waitMyTurn()");
        notifyScheduler = aseTestBridgeClass.getMethod("void notifyScheduler()");
        enterMonitor = aseTestBridgeClass.getMethod("void enterMonitor()");
        exitMonitor = aseTestBridgeClass.getMethod("void exitMonitor()");
        incNumUIBlocks = aseTestBridgeClass.getMethod("void incNumUIBlocks()");
        decNumUIBlocks = aseTestBridgeClass.getMethod("void decNumUIBlocks()");
        setActivityViewTraverser = aseTestBridgeClass.getMethod("void setActivityViewTraverser(android.app.Activity)");
        setFragmentViewTraverser = aseTestBridgeClass.getMethod("void setFragmentViewTraverser(android.view.View)");
        setActionBarMenu = aseTestBridgeClass.getMethod("void setActionBarMenu(android.view.Menu)");
        setRecorderForActionBar = aseTestBridgeClass.getMethod("void setRecorderForActionBar(android.view.MenuItem)");

        activityClass = Scene.v().getSootClass("android.app.Activity");
    }

    @Override
    protected void internalTransform(final Body b, String phaseName,
            @SuppressWarnings("rawtypes") Map options) {

        init();

        String className = b.getMethod().getDeclaringClass().toString();
        String methodName = b.getMethod().getName();

        SootClass clazz = b.getMethod().getDeclaringClass();
        SootClass activityClass = Scene.v().getSootClass("android.app.Activity");

        if (className.startsWith("ase.")) {
            // skip
        } else if (className.startsWith("android.support")) {
            // skip
        } else if (className.startsWith("org.apache")) {
            // skip
        } else if (className.startsWith("org.xml")) {
            // skip
        } else if (className.startsWith("org.json")) {
            // skip
        } else if (hasParentClass(clazz, activityClass) && methodName.equals("onCreate")) {
            instrumentOnCreateMethod(b);

        } else if (methodName.equals("onCreateView")) {
            instrumentOnCreateViewMethod(b);

        } else if (methodName.equals("onCreateOptionsMenu")) {  //////////////////////////
            instrumentonCreateOptionsMenu(b);

        } else if (methodName.equals("onOptionsItemSelected")) {  //////////////////////////
            instrumentOnOptionsItemSelected(b);

        } else if (methodName.equals("doInBackground")) {
            System.out.println("===========Instrumenting " + methodName + "..");
            instrumentDoInBackgMethod(b);

        } else if (methodName.equals("onPostExecute")||
                   methodName.equals("onProgressUpdate")) {
            System.out.println("===========Instrumenting " + methodName + "..");
            instrumentPublishPost(b);

        } else if (methodName.equals("run") ||
                   methodName.equals("handleMessage") ||
                   methodName.equals("handleIntent")) {

            System.out.println("===========Instrumenting " + methodName + "..");
            instrumentMethod(b);

        } 

        // No need to instrument input event handlers any more!
        // The execution order of input event handlers are controlled in blocks
        // that call callOnClick methods for events (posted to UI thread by InputRepeater)
        /*else if (b.getMethod().getDeclaringClass().getSuperclass().toString().equals("android.app.Activity") &&
                !activityClass.declaresMethod(b.getMethod().getNumberedSubSignature()) ){

            // if a method in an Activity class is defined by user, synchronize 
            // it with the scheduler: (so that UI gives change to other threads 
            // when a UI event is received)
            System.out.println("===========Instrumenting a user defined method: " + b.getMethod());
            instrumentMethod(b);
        }*/
    }

    private boolean hasParentClass(SootClass clazz, SootClass ancestor) {
        if(clazz == ancestor)
            return true;
        if(clazz.getName().equalsIgnoreCase("java.lang.Object"))
            return false;
        return hasParentClass(clazz.getSuperclass(), ancestor);
    }
    /**
     * Adds a statement to initiate ase scheduler
     * Also adds a call to setActivityViewTraverser 
     * to set root view of the app and traverse the views in the activity layout
     */
    private void instrumentOnCreateMethod(final Body b) {
        final PatchingChain<Unit> units = b.getUnits();
        Iterator<Unit> iter = units.snapshotIterator();

        // initiate scheduler as the first statement
        // since the latter statements may call async tasks
        Stmt stmt = ((JimpleBody) b).getFirstNonIdentityStmt();
        units.insertBefore(staticInvocation(initiateScheduler, b.getThisLocal()), stmt);
        System.out.println("===========Initiate Scheduler stmt added..");

        while (iter.hasNext()) {
            Unit u = iter.next();
            u.apply(new AbstractStmtSwitch() {

                public void caseReturnVoidStmt(ReturnVoidStmt stmt) {
                    units.insertBefore(staticInvocation(setActivityViewTraverser, b.getThisLocal()), stmt);
                    System.out.println("===========ActivityViewTraversal stmt added..");
                }

            });

        }
    }

    /**
     * Adds call to setFragmentViewTraverser to traverse the views in fragment view
     * to enable record/replay
     */
    private void instrumentOnCreateViewMethod(final Body b) {
        final PatchingChain<Unit> units = b.getUnits();
        Iterator<Unit> iter = units.snapshotIterator();

        while (iter.hasNext()) {

            iter.next().apply(new AbstractStmtSwitch() {

                public void caseReturnStmt(ReturnStmt stmt) {
                    
                    //read to-be-returned value
                    Value returnedView = stmt.getOpBox().getValue();
                    // stmt.getReturnExpr().getArg(0);
                    // insert call to setFragmentViewTraverser
                    units.insertBefore(staticInvocation(setFragmentViewTraverser, returnedView), stmt);
                    System.out.println("===========FragmentViewTraversal stmt added..");

                }
            });
        }
    }
    
    /**
     * Execution of that method is controlled by ase.scheduler
     * (its code is executed in between waitMyTurn() and notifyScheduler())
     * It also increases numUIBlocks when:
     *  - it publishes progress (onPublishProgress will be executed on UI thread) or
     *  - it returns (onPostExecute will be executed on UI thread
     */
    private void instrumentDoInBackgMethod(final Body b) {
        final PatchingChain<Unit> units = b.getUnits();
        Iterator<Unit> iter = units.snapshotIterator();

        if(!iter.hasNext())
            return;
        
        Unit u = iter.next();
        units.insertAfter(staticInvocation(waitMyTurn), u);
        System.out.println("Wait for CPU stmt added..");

        while (iter.hasNext()) {
            u = iter.next();
            u.apply(new AbstractStmtSwitch() {

                // increment numUIBlocks when publishes progress (posts onPublishProgress)
                // and when it returns (posts onPostExecute)
                public void caseInvokeStmt(InvokeStmt stmt) {
                    if (stmt.getInvokeExpr().getMethod().getName().equals("publishProgress")){
                        units.insertAfter(staticInvocation(incNumUIBlocks), stmt);
                        units.insertAfter(staticInvocation(incNumUIBlocks), stmt);
                        System.out.println("Increment numUIBlocks stmt added after publishProgress..");
                    }
                }
                
                public void caseReturnVoidStmt(ReturnVoidStmt stmt) {
                    units.insertBefore(staticInvocation(incNumUIBlocks), stmt);
                    System.out.println("Increment numUIBlocks stmt added after doInBackGround..");
                    units.insertBefore(staticInvocation(notifyScheduler), stmt);
                    System.out.println("Release CPU stmt added..");
                }
                
                public void caseReturnStmt(ReturnStmt stmt) {
                    units.insertBefore(staticInvocation(incNumUIBlocks), stmt);
                    System.out.println("Increment numUIBlocks stmt added after doInBackGround..");
                    units.insertBefore(staticInvocation(notifyScheduler), stmt);
                    System.out.println("Release CPU stmt added..");
                }
                
                public void caseRetStmt(RetStmt stmt) {
                    units.insertBefore(staticInvocation(incNumUIBlocks), stmt);
                    System.out.println("Increment numUIBlocks stmt added after doInBackGround..");
                    units.insertBefore(staticInvocation(notifyScheduler), stmt);
                    System.out.println("Release CPU stmt added..");
                }
                
                public void caseEnterMonitorStmt(EnterMonitorStmt stmt){
                    units.insertAfter(staticInvocation(enterMonitor), stmt);
                    System.out.println("Enter monitor stmt added..");
                }
                
                public void caseExitMonitorStmt(ExitMonitorStmt stmt){
                    units.insertAfter(staticInvocation(exitMonitor), stmt);
                    System.out.println("Exit monitor stmt added..");
                }
            });
        }
    }

    /**
     * Execution of that method is controlled by ase.scheduler
     * (its code is executed in between waitMyTurn() and notifyScheduler())
     */
    private void instrumentMethod(final Body b){
        final PatchingChain<Unit> units = b.getUnits();
        Iterator<Unit> iter = units.snapshotIterator();

        if(!iter.hasNext())
            return;
        
        Unit u = iter.next();
        units.insertAfter(staticInvocation(waitMyTurn), u);
        System.out.println("Wait for CPU stmt added..");

        while (iter.hasNext()) {
            u = iter.next();
            u.apply(new AbstractStmtSwitch() {

                public void caseReturnVoidStmt(ReturnVoidStmt stmt) {
                    units.insertBefore(staticInvocation(notifyScheduler), stmt);
                    System.out.println("Release CPU stmt added..");
                }
                
                public void caseReturnStmt(ReturnStmt stmt) {
                    units.insertBefore(staticInvocation(notifyScheduler), stmt);
                    System.out.println("Release CPU stmt added..");
                }
                
                public void caseRetStmt(RetStmt stmt) {
                    units.insertBefore(staticInvocation(notifyScheduler), stmt);
                    System.out.println("Release CPU stmt added..");
                }
                
                public void caseEnterMonitorStmt(EnterMonitorStmt stmt){
                    units.insertAfter(staticInvocation(enterMonitor), stmt);
                    System.out.println("Enter monitor stmt added..");
                }
                
                public void caseExitMonitorStmt(ExitMonitorStmt stmt){
                    units.insertAfter(staticInvocation(exitMonitor), stmt);
                    System.out.println("Exit monitor stmt added..");
                }
            });
        }
    }

    /**
     * Execution of that method is controlled by ase.scheduler
     * (its code is executed in between waitMyTurn() and notifyScheduler())
     */
    private void instrumentPublishPost(final Body b){
        final PatchingChain<Unit> units = b.getUnits();
        Iterator<Unit> iter = units.snapshotIterator();

        if(!iter.hasNext())
            return;

        Unit u = iter.next();
        units.insertAfter(staticInvocation(waitMyTurn), u);
        System.out.println("Wait for CPU stmt added..");

        while (iter.hasNext()) {
            u = iter.next();
            u.apply(new AbstractStmtSwitch() {

                public void caseReturnVoidStmt(ReturnVoidStmt stmt) {
                    units.insertBefore(staticInvocation(decNumUIBlocks), stmt);
                    System.out.println("Decrement numUIBlocks stmt added..");
                    units.insertBefore(staticInvocation(notifyScheduler), stmt);
                    System.out.println("Release CPU stmt added..");
                }

                public void caseReturnStmt(ReturnStmt stmt) {
                    units.insertBefore(staticInvocation(decNumUIBlocks), stmt);
                    System.out.println("Decrement numUIBlocks stmt added..");
                    units.insertBefore(staticInvocation(notifyScheduler), stmt);
                    System.out.println("Release CPU stmt added..");
                }

                public void caseRetStmt(RetStmt stmt) {
                    units.insertBefore(staticInvocation(decNumUIBlocks), stmt);
                    System.out.println("Decrement numUIBlocks stmt added..");
                    units.insertBefore(staticInvocation(notifyScheduler), stmt);
                    System.out.println("Release CPU stmt added..");
                }

                public void caseEnterMonitorStmt(EnterMonitorStmt stmt){
                    units.insertAfter(staticInvocation(enterMonitor), stmt);
                    System.out.println("Enter monitor stmt added..");
                }

                public void caseExitMonitorStmt(ExitMonitorStmt stmt){
                    units.insertAfter(staticInvocation(exitMonitor), stmt);
                    System.out.println("Exit monitor stmt added..");
                }
            });
        }
    }

    private void instrumentonCreateOptionsMenu(final Body b) {
        final PatchingChain<Unit> units = b.getUnits();
        Iterator<Unit> iter = units.snapshotIterator();

        iter.next(); // the identity statement for the method
        JIdentityStmt stmt = (JIdentityStmt) iter.next(); // the identity statement for the parameter
        Value param = stmt.getLeftOp();

        units.insertAfter(staticInvocation(setActionBarMenu, param), stmt);
        System.out.println("===========Action bar menu is set..");

    }
    private void instrumentOnOptionsItemSelected(final Body b) {
        final PatchingChain<Unit> units = b.getUnits();
        Iterator<Unit> iter = units.snapshotIterator();

        iter.next(); // the identity statement for the method
        JIdentityStmt stmt = (JIdentityStmt) iter.next(); // the identity statement for the parameter
        Value param = stmt.getLeftOp();

        units.insertAfter(staticInvocation(setRecorderForActionBar, param), stmt);
        System.out.println("===========Action bar recorder is added..");

    }

    private Local createLocal(Body body, String name, String type) {
        Local l = Jimple.v().newLocal(name, RefType.v(type));
        body.getLocals().add(l);
        return l;
    }

    private static Local createLocal(Body body, String name, RefType type) {
        Local l = Jimple.v().newLocal(name, type);
        body.getLocals().add(l);
        return l;
    } 

    private InvokeStmt staticInvocation(SootMethod m) {
        return Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(m.makeRef()));
    }

    private InvokeStmt staticInvocation(SootMethod m, Local arg) {
        return Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(m.makeRef(),arg));
    }
    
    private InvokeStmt staticInvocation(SootMethod m, Value arg) {
        return Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(m.makeRef(),arg));
    }

}
