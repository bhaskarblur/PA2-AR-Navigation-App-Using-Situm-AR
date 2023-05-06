package com.example.indoortracking;

import com.example.indoortracking.assets.MainActivity;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.sceneform.ux.ArFragment;

public class customARFrag extends ArFragment {

    @Override
    protected Config getSessionConfiguration(Session session) {
        Config config=new Config(session);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        config.setFocusMode(Config.FocusMode.AUTO);
        session.configure(config);
        this.getArSceneView().setupSession(session);
   //     ( (ARActivity) getActivity()).setupImage(config,session);
        return config;
    }
}
