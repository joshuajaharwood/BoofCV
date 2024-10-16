/*
 * Copyright (c) 2024, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.geo.bundle;

import boofcv.abst.geo.bundle.SceneObservations;
import boofcv.abst.geo.bundle.SceneStructureCommon;
import boofcv.abst.geo.bundle.SceneStructureMetric;
import boofcv.alg.geo.WorldToCameraToPixel;
import boofcv.alg.geo.bundle.cameras.BundleZoomState;
import boofcv.struct.calib.CameraPinholeBrown;
import boofcv.testing.BoofStandardJUnit;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point4D_F64;
import georegression.struct.se.SpecialEuclideanOps_F64;
import org.ejml.UtilEjml;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static boofcv.alg.geo.bundle.TestCodecSceneStructureMetric.createScene;
import static boofcv.alg.geo.bundle.TestCodecSceneStructureMetric.createSceneZoomState;
import static org.junit.jupiter.api.Assertions.*;

class TestBundleAdjustmentMetricResidualFunction extends BoofStandardJUnit {
	/**
	 * Makes sure that when given the same input it produces the same output
	 */
	@Test void multipleCalls() {
		multipleCalls(true, false, false);
		multipleCalls(false, false, false);
		multipleCalls(true, true, false);
		multipleCalls(false, true, false);
		multipleCalls(true, false, true);
		multipleCalls(false, false, true);
	}

	void multipleCalls( boolean homogeneous, boolean hasRigid, boolean hasRelative ) {
		SceneStructureMetric structure = createScene(rand, homogeneous, hasRigid, hasRelative);
		SceneObservations obs = createObservations(rand, structure);

		var param = new double[structure.getParameterCount()];

		new CodecSceneStructureMetric().encode(structure, param);

		var alg = new BundleAdjustmentMetricResidualFunction();
		alg.configure(structure, obs);

		var expected = new double[alg.getNumOfOutputsM()];
		var found = new double[alg.getNumOfOutputsM()];

		alg.process(param, expected);
		alg.process(param, found);

		assertArrayEquals(expected, found, UtilEjml.TEST_F64);
	}

	/**
	 * Change each parameter and see if it changes the output
	 */
	@Test void changeInParamChangesOutput() {
		changeInParamChangesOutput(true);
		changeInParamChangesOutput(false);
	}

	void changeInParamChangesOutput( boolean homogeneous ) {
		SceneStructureMetric structure = createScene(rand, homogeneous, false, false);
		var param = new double[structure.getParameterCount()];

		new CodecSceneStructureMetric().encode(structure, param);

		// Create random observations
		SceneObservations obs = createObservations(rand, structure);

		var alg = new BundleAdjustmentMetricResidualFunction();
		alg.configure(structure, obs);

		var original = new double[alg.getNumOfOutputsM()];
		var found = new double[alg.getNumOfOutputsM()];
		alg.process(param, original);

		for (int paramIndex = 0; paramIndex < original.length; paramIndex++) {
			double v = param[paramIndex];
			param[paramIndex] += 0.001;
			alg.process(param, found);

			boolean identical = true;
			for (int i = 0; i < found.length; i++) {
				if (Math.abs(original[i] - found[i]) > UtilEjml.TEST_F64) {
					identical = false;
					break;
				}
			}
			assertFalse(identical);
			param[paramIndex] = v;
		}
	}

	/**
	 * Multiple views are relative to each other. See if changing the value in an earlier view affects the final
	 * output down the chain.
	 */
	@Test void chainedRelativeViews() {
		chainedRelativeViews(true);
		chainedRelativeViews(false);
	}

	void chainedRelativeViews( boolean homogeneous ) {
		SceneStructureMetric structure = createScene(rand, homogeneous, false, false);
		// Make each view be relative to the previous view
		structure.views.forIdx(( i, v ) -> v.parent = i > 0 ? structure.views.data[i - 1] : null);

		var param = new double[structure.getParameterCount()];

		new CodecSceneStructureMetric().encode(structure, param);

		// Create random observations
		SceneObservations obs = createObservations(rand, structure);

		var alg = new BundleAdjustmentMetricResidualFunction();
		alg.configure(structure, obs);

		var original = new double[alg.getNumOfOutputsM()];
		var found = new double[alg.getNumOfOutputsM()];
		alg.process(param, original);

		// Now change view[1]. This should change residuals in 1,2,3 but not 0
		structure.getParentToView(1).T.x += 0.2;
		structure.getParentToView(1).T.y += 0.2;

		new CodecSceneStructureMetric().encode(structure, param);
		alg.process(param, found);

		// values should not be the same after this index
		int changeAfterIndex = obs.getView(0).size()*2;

		for (int i = 0; i < found.length; i++) {
			if (i < changeAfterIndex) {
				assertEquals(original[i], found[i], UtilEjml.TEST_F64);
			} else {
				assertNotEquals(original[i], found[i], UtilEjml.TEST_F64);
			}
		}
	}

	/**
	 * Provide a camera model that will fail if the camera state isn't handled correctly
	 */
	@Test void cameraState() {
		cameraState(true);
		cameraState(false);
	}

	void cameraState( boolean homogeneous ) {
		SceneStructureMetric structure = createSceneZoomState(rand, homogeneous);
		SceneObservations observations = createObservations(rand, structure);

		// Add the camera state to all observation views
		observations.views.forEach(v -> v.cameraState = new BundleZoomState(400.0));

		var alg = new BundleAdjustmentMetricResidualFunction();
		alg.configure(structure, observations);

		var param = new double[structure.getParameterCount()];
		new CodecSceneStructureMetric().encode(structure, param);

		var residuals = new double[alg.getNumOfOutputsM()];
		alg.process(param, residuals);

		// If the camera state isn't set this should be NaN
		for (int i = 0; i < residuals.length; i++) {
			assertFalse(UtilEjml.isUncountable(residuals[i]));
		}
	}

	static SceneObservations createObservations( Random rand, SceneStructureMetric structure ) {
		var obs = new SceneObservations();
		obs.initialize(structure.views.size, structure.hasRigid());

		for (int j = 0; j < structure.points.size; j++) {
			SceneStructureCommon.Point p = structure.points.data[j];

			for (int i = 0; i < p.views.size; i++) {
				SceneObservations.View v = obs.getView(p.views.get(i));
				v.point.add(j);
				v.observations.add(rand.nextInt(300) + 20);
				v.observations.add(rand.nextInt(300) + 20);
			}
		}

		if (structure.hasRigid()) {
			for (int indexRigid = 0; indexRigid < structure.rigids.size; indexRigid++) {
				SceneStructureMetric.Rigid r = structure.rigids.data[indexRigid];
				for (int i = 0; i < r.points.length; i++) {
					SceneStructureCommon.Point p = r.points[i];
					int indexPoint = r.indexFirst + i;

					for (int j = 0; j < p.views.size; j++) {
						SceneObservations.View v = obs.getViewRigid(p.views.get(j));
						v.point.add(indexPoint);
						v.observations.add(rand.nextInt(300) + 20);
						v.observations.add(rand.nextInt(300) + 20);
					}
				}
			}
		}

		return obs;
	}

	/**
	 * Test to see if rigid objects are projected correctly. This is related to a bug that was found.
	 */
	@Test void rigidObjectProjectionH() {
		// Create a simple scene with a simple rigid object
		var worldToObject = SpecialEuclideanOps_F64.eulerXyz(0.1, -0.05, 0.04, 0.04, -0.03, 0.09, null);
		var worldToView = SpecialEuclideanOps_F64.eulerXyz(0.15, -0.08, 0.04, 0.09, -0.01, 0.09, null);
		var brown = new CameraPinholeBrown().fsetK(500, 500, 0, 250, 250, 1000, 1000).fsetRadial(0.005, -0.004);

		var structure = new SceneStructureMetric(true);
		structure.initialize(1, 1, 1, 0, 1);
		structure.setRigid(0, true, worldToObject, 1);
		structure.setView(0, 0, true, worldToView);
		structure.setCamera(0, true, brown);
		structure.assignIDsToRigidPoints();
		structure.getRigid(0).setPoint(0, 0.2, 0.3, -1.1, -0.96);

		structure.rigids.get(0).connectPointToView(0, 0);

		SceneObservations obs = createObservations(rand, structure);

		var param = new double[structure.getParameterCount()];

		new CodecSceneStructureMetric().encode(structure, param);

		var alg = new BundleAdjustmentMetricResidualFunction();
		alg.configure(structure, obs);

		var found = new double[alg.getNumOfOutputsM()];
		alg.process(param, found);

		var rigid4 = new Point4D_F64();
		var world4 = new Point4D_F64();
		var predicted = new Point2D_F64();
		var observed = new Point2D_F64();
		var w2p = new WorldToCameraToPixel();
		w2p.configure(brown, worldToView);
		for (int i = 0; i < structure.rigids.size; i++) {
			// For the one point on the rigid object, compute where it should be
			SceneStructureMetric.Rigid rigid = structure.rigids.get(i);
			rigid.getPoint(0, rigid4);
			worldToObject.transform(rigid4, world4);
			assertTrue(w2p.transform(world4, predicted));

			obs.viewsRigid.get(0).getPixel(0, observed);
			assertEquals(predicted.x - observed.x, found[0], UtilEjml.TEST_F64);
			assertEquals(predicted.y - observed.y, found[1], UtilEjml.TEST_F64);
		}
	}
}
