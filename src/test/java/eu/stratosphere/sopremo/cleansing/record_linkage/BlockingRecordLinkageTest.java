package eu.stratosphere.sopremo.cleansing.record_linkage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.runners.Parameterized.Parameters;

import eu.stratosphere.sopremo.cleansing.duplicatedection.*;
import eu.stratosphere.sopremo.expressions.ArrayCreation;
import eu.stratosphere.sopremo.expressions.EvaluationExpression;
import eu.stratosphere.sopremo.expressions.ObjectAccess;
import eu.stratosphere.sopremo.type.IJsonNode;

/**
 * Tests {@link Blocking} with two data sources.
 * 
 * @author Arvid Heise
 */
public class BlockingRecordLinkageTest extends RecordLinkageTestBase<Blocking> {

	private final EvaluationExpression[] leftBlockingKeys, rightBlockingKeys;

	/**
	 * Initializes NaiveRecordLinkageInterSourceTest with the given parameter
	 * 
	 * @param blockingKeys
	 */
	public BlockingRecordLinkageTest(final EvaluationExpression resultProjection, final String[][] blockingKeys) {
		super(resultProjection);

		this.leftBlockingKeys = new EvaluationExpression[blockingKeys[0].length];
		for (int index = 0; index < this.leftBlockingKeys.length; index++)
			this.leftBlockingKeys[index] = new ObjectAccess(blockingKeys[0][index]);
		this.rightBlockingKeys = new EvaluationExpression[blockingKeys[1].length];
		for (int index = 0; index < this.rightBlockingKeys.length; index++)
			this.rightBlockingKeys[index] = new ObjectAccess(blockingKeys[1][index]);

	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.cleansing.record_linkage.DuplicateDetectionTestBase#getCandidateSelection()
	 */
	@Override
	protected CandidateSelection getCandidateSelection() {
		final CandidateSelection candidateSelection = super.getCandidateSelection();
		for (int index = 0; index < this.leftBlockingKeys.length; index++)
			candidateSelection.withPass(this.leftBlockingKeys[index], this.rightBlockingKeys[index]);
		return candidateSelection;
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * eu.stratosphere.sopremo.cleansing.record_linkage.RecordLinkageTestBase#generateExpectedPairs(eu.stratosphere.
	 * sopremo.testing.SopremoTestPlan.Input, eu.stratosphere.sopremo.SopremoTestPlan.Input,
	 * eu.stratosphere.sopremo.cleansing.duplicatedection.CandidateComparison)
	 */
	@Override
	protected void generateExpectedPairs(List<IJsonNode> leftInput, List<IJsonNode> rightInput,
			CandidateComparison candidateComparison) {
		for (final IJsonNode left : leftInput)
			for (final IJsonNode right : rightInput) {
				for (int index = 0; index < this.leftBlockingKeys.length; index++)
					if (this.leftBlockingKeys[index].evaluate(left).equals(
						this.rightBlockingKeys[index].evaluate(right)))
						this.emitCandidate(left, right);
			}

	}

	@Override
	protected CompositeDuplicateDetectionAlgorithm<?> getImplementation() {
		return new Blocking();
	}

	@Override
	public String toString() {
		return String.format("%s, leftBlockingKeys=%s, rightBlockingKeys=%s", super.toString(),
			Arrays.toString(this.leftBlockingKeys), Arrays.toString(this.rightBlockingKeys));
	}

	/**
	 * Returns the parameter combination under test.
	 * 
	 * @return the parameter combination
	 */
	@Parameters
	public static Collection<Object[]> getParameters() {
		final EvaluationExpression[] projections = { null,
			new ArrayCreation(getAggregativeProjection1(), getAggregativeProjection2()), };

		final ArrayList<Object[]> parameters = new ArrayList<Object[]>();
		for (final EvaluationExpression projection : projections)
			for (final String[][] combinedBlockingKeys : TestKeys.CombinedBlockingKeys)
				parameters.add(new Object[] { projection, combinedBlockingKeys });

		return parameters;
	}
}
