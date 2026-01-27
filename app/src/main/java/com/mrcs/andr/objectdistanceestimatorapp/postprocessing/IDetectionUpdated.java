package com.mrcs.andr.objectdistanceestimatorapp.postprocessing;

import java.util.List;

public interface IDetectionUpdated {

    void onDetectionUpdated(List<Detection> detections);

}
