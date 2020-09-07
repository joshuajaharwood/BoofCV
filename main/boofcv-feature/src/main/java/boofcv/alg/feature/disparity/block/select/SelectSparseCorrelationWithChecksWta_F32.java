/*
 * Copyright (c) 2011-2020, Peter Abeles. All Rights Reserved.
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

package boofcv.alg.feature.disparity.block.select;

import boofcv.alg.feature.disparity.block.SelectSparseStandardWta;
import boofcv.alg.feature.disparity.block.score.DisparitySparseRectifiedScoreBM;

/**
 * Selects the best correlation score with sanity checks.
 *
 * @author Peter Abeles
 */
public class SelectSparseCorrelationWithChecksWta_F32 extends SelectSparseStandardWta<float[]> {

	// texture threshold
	protected float textureThreshold;

	public SelectSparseCorrelationWithChecksWta_F32( double texture, int tolRightToLeft ) {
		super(0, texture, tolRightToLeft);
	}

	@Override
	protected void setTexture( double texture ) {
		this.textureThreshold = (float)texture;
	}

	@Override
	public boolean select( DisparitySparseRectifiedScoreBM<float[], ?> scorer, int x, int y ) {
		// First compute the error in the normal left to right direction
		if (!scorer.processLeftToRight(x, y))
			return false;
		float[] scores = scorer.getScoreLtoR();
		int disparityRange = scorer.getLocalRangeLtoR();

		// Select the disparity with the best error
		int bestDisparity = 0;
		float scoreBest = scores[0];
		float scoreWorst = scoreBest;

		for (int i = 1; i < disparityRange; i++) {
			float s = scores[i];
			if (s > scoreBest) {
				scoreBest = scores[i];
				bestDisparity = i;
			} else if (s < scoreWorst) {
				scoreWorst = s;
			}
		}

		// test to see if the region lacks sufficient texture if:
		// 1) not already eliminated 2) sufficient disparities to check, 3) it's activated
		if (textureThreshold > 0 && disparityRange >= 3) {
			// find the second best disparity value and exclude its neighbors
			float secondBest = scoreWorst;
			for (int i = 0; i < bestDisparity - 1; i++) {
				if (scores[i] > secondBest)
					secondBest = scores[i];
			}
			for (int i = bestDisparity + 2; i < disparityRange; i++) {
				if (scores[i] > secondBest)
					secondBest = scores[i];
			}

			// Make the score relative to the worst score
			scoreBest -= scoreWorst;
			secondBest -= scoreWorst;

			// similar scores indicate lack of texture
			// C = (C2-C1)/C1
			if (scoreBest - secondBest <= textureThreshold*secondBest)
				return false;
		}

		// if requested perform right to left validation. Ideally the two disparities will be identical
		if (tolRightToLeft >= 0) {
			if (!scorer.processRightToLeft(x - bestDisparity - scorer.getDisparityMin(), y))
				return false;
			final float[] scoresRtoL = scorer.getScoreRtoL();
			final int localRangeRtoL = scorer.getLocalRangeRtoL();
			int bestDisparityRtoL = 0;
			float scoreBestRtoL = scoresRtoL[0];

			for (int i = 1; i < localRangeRtoL; i++) {
				float s = scoresRtoL[i];
				if (s > scoreBestRtoL) {
					scoreBestRtoL = s;
					bestDisparityRtoL = i;
				}
			}
			if (Math.abs(bestDisparityRtoL - bestDisparity) > tolRightToLeft)
				return false;
		}

		this.disparity = bestDisparity;
		return true;
	}
}
