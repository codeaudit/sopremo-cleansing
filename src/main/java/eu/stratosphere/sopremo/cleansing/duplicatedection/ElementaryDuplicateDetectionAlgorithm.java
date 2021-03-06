/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/
package eu.stratosphere.sopremo.cleansing.duplicatedection;

import eu.stratosphere.sopremo.operator.ElementaryOperator;

/**
 * @author Arvid Heise
 */
public abstract class ElementaryDuplicateDetectionAlgorithm<ImplType extends ElementaryDuplicateDetectionAlgorithm<ImplType>>
		extends ElementaryOperator<ImplType> {
	private PairFilter pairFilter = new PairFilter();

	private CandidateComparison candidateComparison = new CandidateComparison();

	/**
	 * Returns the value of candidateComparison.
	 * 
	 * @return the candidateComparison
	 */
	public CandidateComparison getCandidateComparison() {
		return this.candidateComparison;
	}

	/**
	 * Returns the pairFilter.
	 * 
	 * @return the pairFilter
	 */
	public PairFilter getPairFilter() {
		return this.pairFilter;
	}

	/**
	 * Sets the value of candidateComparison to the given value.
	 * 
	 * @param candidateComparison
	 *        the candidateComparison to set
	 */
	public void setCandidateComparison(final CandidateComparison candidateComparison) {
		if (candidateComparison == null)
			throw new NullPointerException("candidateComparison must not be null");

		this.candidateComparison = candidateComparison;
	}

	/**
	 * Sets the pairFilter to the specified value.
	 * 
	 * @param pairFilter
	 *        the pairFilter to set
	 */
	public void setPairFilter(final PairFilter pairFilter) {
		if (pairFilter == null)
			throw new NullPointerException("pairFilter must not be null");

		this.pairFilter = pairFilter;
	}

	/**
	 * Sets the value of candidateComparison to the given value.
	 * 
	 * @param candidateComparison
	 *        the candidateComparison to set
	 * @return this
	 */
	public ImplType withCandidateComparison(final CandidateComparison candidateComparison) {
		this.setCandidateComparison(candidateComparison);
		return this.self();
	}

	/**
	 * Sets the pairFilter to the specified value.
	 * 
	 * @param pairFilter
	 *        the pairFilter to set
	 */
	public ImplType withPairFilter(final PairFilter pairFilter) {
		this.setPairFilter(pairFilter);
		return this.self();
	}
}
