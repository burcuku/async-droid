package ase.event;

import android.util.Log;
import android.view.View;
import ase.AppRunTimeData;

/**
 * Created by burcuozkan on 06/12/14.
 */
public class AseClickEvent extends AseEvent {

    public AseClickEvent(int viewId) {
        super(EventType.CLICK, viewId);
    }

    @Override
    public String toString() {
        return String.format("%s %d In fragment: %s", type.name(), viewId, fragmentName);
    }

    @Override
    public boolean isFirable() {
        View view = AppRunTimeData.getInstance().getActivityRootView().findViewById(viewId);
        return super.isFirable() && (view != null);
    }

    @Override
    public void injectEvent() {
        View view = AppRunTimeData.getInstance().getActivityRootView().findViewById(viewId);
        //view.callOnClick();
        view.performClick();
        Log.i("Repeater", "Clicked view: " + Integer.toHexString(view.getId()));
    }
}