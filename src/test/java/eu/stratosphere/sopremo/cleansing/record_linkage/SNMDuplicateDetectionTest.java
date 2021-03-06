package eu.stratosphere.sopremo.cleansing.record_linkage;

import java.util.*;

import org.junit.runners.Parameterized.Parameters;

import eu.stratosphere.sopremo.cleansing.duplicatedection.*;
import eu.stratosphere.sopremo.expressions.ArrayCreation;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.expressions.ObjectAccess;
import eu.stratosphere.sopremo.type.IJsonNode;

/**
 * Tests {@link SortedNeighborhood} within one data source.
 * 
 * @author Arvid Heise
 */
public class SNMDuplicateDetectionTest extends DuplicateDetectionTestBase<Blocking> {
	private final EvaluationExpression[] sortingKeys;

	private final int windowSize;

	/**
	 * Initializes NaiveRecordLinkageInterSourceTest with the given parameter
	 * 
	 * @param projection
	 */
	public SNMDuplicateDetectionTest(final EvaluationExpression projection,
			final int windowSize, final String[][] sortingKeys) {
		super(projection, true);

		this.sortingKeys = new EvaluationExpression[sortingKeys[0].length];
		for (int index = 0; index < this.sortingKeys.length; index++) {
			this.sortingKeys[index] =
				new ArrayCreation(new ObjectAccess(sortingKeys[0][index]), new ObjectAccess("id"));
		}
		this.windowSize = windowSize;
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.cleansing.record_linkage.DuplicateDetectionTestBase#getCandidateSelection()
	 */
	@Override
	protected CandidateSelection getCandidateSelection() {
		final CandidateSelection candidateSelection = super.getCandidateSelection();
		for (EvaluationExpression blockingKey : this.sortingKeys)
			candidateSelection.withPass(blockingKey);
		return candidateSelection;
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.sopremo.cleansing.record_linkage.DuplicateDetectionTestBase#generateExpectedPairs(java.util.List,
	 * eu.stratosphere.sopremo.cleansing.duplicatedection.PairFilter,
	 * eu.stratosphere.sopremo.cleansing.duplicatedection.CandidateComparison)
	 */
	@Override
	protected void generateExpectedPairs(List<IJsonNode> input, PairFilter pairFilter, CandidateComparison comparison) {
		for (final EvaluationExpression sortingKey : this.sortingKeys) {
			Collections.sort(input, new ExpressionSorter(sortingKey));

			for (int index1 = 0, size = input.size(); index1 < size; index1++) {
				IJsonNode left = input.get(index1);
				for (int index2 = index1 + 1; index2 < Math.min(size, index1 + this.windowSize); index2++) {
					IJsonNode right = input.get(index2);
					comparison.performComparison(left, right, this.duplicateCollector);
				}
			}
		}
	}

	@Override
	protected CompositeDuplicateDetectionAlgorithm<?> getImplementation() {
		return new SortedNeighborhood().withWindowSize(this.windowSize);
	}

	@Override
	public String toString() {
		return String.format("%s, blockingKeys=%s", super.toString(), Arrays.toString(this.sortingKeys));
	}

	/**
	 * Returns the parameter combination under test.
	 * 
	 * @return the parameter combination
	 */
	@Parameters
	public static Collection<Object[]> getParameters() {
		final EvaluationExpression[] projections = { null, getAggregativeProjection() };

		final ArrayList<Object[]> parameters = new ArrayList<Object[]>();
		for (final EvaluationExpression projection : projections)
			for (final String[][] combinedBlockingKeys : TestKeys.CombinedBlockingKeys)
				for (final int windowSize : new int[] { 2, 3 })
					parameters.add(new Object[] { projection, windowSize, combinedBlockingKeys });

		return parameters;
	}

}
