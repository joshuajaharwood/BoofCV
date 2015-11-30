/*
 * Copyright (c) 2011-2015, Peter Abeles. All Rights Reserved.
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

package boofcv.alg.feature.orientation.impl;

import boofcv.alg.feature.orientation.GenericOrientationGradientTests;
import boofcv.struct.image.ImageFloat32;
import org.junit.Test;


/**
 * @author Peter Abeles
 */
public class TestImplOrientationHistogram_F32 {

	int N = 10;
	int r = 3;

	@Test
	public void standardUnweighted() {
		GenericOrientationGradientTests<ImageFloat32> tests = new GenericOrientationGradientTests<ImageFloat32>();

		ImplOrientationHistogram_F32 alg = new ImplOrientationHistogram_F32(1.0/2.0,N,false);
		alg.setObjectToSample(r);

		tests.setup(2.0*Math.PI/N, r*2+1 , alg);
		tests.performAll();
	}

	@Test
	public void standardWeighted() {
		GenericOrientationGradientTests<ImageFloat32> tests = new GenericOrientationGradientTests<ImageFloat32>();

		ImplOrientationHistogram_F32 alg = new ImplOrientationHistogram_F32(1.0/2.0,N,true);
		alg.setObjectToSample(r);

		tests.setup(2.0*Math.PI/N, r*2+1 ,alg);
		tests.performAll();

	}

}
