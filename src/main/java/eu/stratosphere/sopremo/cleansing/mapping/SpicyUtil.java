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
package eu.stratosphere.sopremo.cleansing.mapping;

import static eu.stratosphere.sopremo.pact.SopremoUtil.cast;
import it.unibas.spicy.model.algebra.query.operators.sql.GenerateSQL;
import it.unibas.spicy.model.algebra.query.operators.xquery.XQNames;
import it.unibas.spicy.model.algebra.query.operators.xquery.XQUtility;
import it.unibas.spicy.model.datasource.INode;
import it.unibas.spicy.model.datasource.nodes.AttributeNode;
import it.unibas.spicy.model.datasource.nodes.LeafNode;
import it.unibas.spicy.model.datasource.nodes.SequenceNode;
import it.unibas.spicy.model.datasource.nodes.SetNode;
import it.unibas.spicy.model.expressions.Expression;
import it.unibas.spicy.model.generators.AppendSkolemPart;
import it.unibas.spicy.model.generators.FunctionGenerator;
import it.unibas.spicy.model.generators.GeneratorWithPath;
import it.unibas.spicy.model.generators.ISkolemPart;
import it.unibas.spicy.model.generators.IValueGenerator;
import it.unibas.spicy.model.generators.NullValueGenerator;
import it.unibas.spicy.model.generators.SkolemFunctionGenerator;
import it.unibas.spicy.model.generators.StringSkolemPart;
import it.unibas.spicy.model.mapping.FORule;
import it.unibas.spicy.model.mapping.IDataSourceProxy;
import it.unibas.spicy.model.mapping.MappingTask;
import it.unibas.spicy.model.paths.PathExpression;
import it.unibas.spicy.model.paths.SetAlias;
import it.unibas.spicy.model.paths.VariableCorrespondence;
import it.unibas.spicy.model.paths.VariablePathExpression;
import it.unibas.spicy.model.paths.operators.GeneratePathExpression;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

import org.nfunk.jep.ASTConstant;
import org.nfunk.jep.ASTFunNode;
import org.nfunk.jep.JEP;
import org.nfunk.jep.Node;

import com.google.common.collect.Iterables;

import eu.stratosphere.sopremo.CoreFunctions;
import eu.stratosphere.sopremo.base.ArraySplit;
import eu.stratosphere.sopremo.expressions.*;
import eu.stratosphere.sopremo.expressions.EvaluationExpression.ValueExpression;
import eu.stratosphere.sopremo.expressions.ObjectCreation.FieldAssignment;
import eu.stratosphere.sopremo.expressions.ObjectCreation.Mapping;
import eu.stratosphere.sopremo.function.FunctionUtil;
import eu.stratosphere.sopremo.operator.JsonStream;
import eu.stratosphere.sopremo.tree.NodeHandler;
import eu.stratosphere.sopremo.tree.ReturnLessNodeHandler;
import eu.stratosphere.sopremo.tree.ReturnlessTreeHandler;
import eu.stratosphere.sopremo.tree.TreeHandler;
import eu.stratosphere.sopremo.type.JsonUtil;

/**
 * @author arvid
 */
public class SpicyUtil {

	public static EvaluationExpression fromExpression(Expression condition) {
		final JEP jepExpression = condition.getJepExpression();
		final Node topNode = jepExpression.getTopNode();
		if (topNode instanceof FunctionNode) {
			final FunctionNode fnNode = (FunctionNode) topNode;
			return fnNode.getExpression();
		} else if (topNode instanceof ASTConstant)
			return new ConstantExpression(((ASTConstant) topNode).getValue());
		else if (topNode instanceof ASTFunNode)
			return JepFunctionFactory.create((ASTFunNode) topNode, condition.getAttributePaths());
		throw new IllegalStateException("Unknown node type");
	}

	public static EvaluationExpression valueForIntermediateNode(IValueGenerator generator, MappingTask mappingTask,
			InputManager inputManager) {

		return valueForLeaf(generator, null, null, mappingTask);
	}

	public static EvaluationExpression valueForLeaf(IValueGenerator generator, VariablePathExpression targetPath,
			FORule tgd, MappingTask mappingTask) {
		if (generator instanceof NullValueGenerator)
			return ConstantExpression.NULL;
		if (generator instanceof FunctionGenerator) {
			VariableCorrespondence correspondence =
				XQUtility.findCorrespondenceFromTargetPathWithSameId(targetPath, tgd.getCoveredCorrespondences());
			EvaluationExpression expr =
				((SopremoFunctionExpression) ((FunctionGenerator) generator).getFunction()).getExpr().clone();
			if (correspondence == null || correspondence.isConstant())
				return expr;
			List<VariablePathExpression> sourcePaths = correspondence.getSourcePaths();
			for (int index = 0; index < sourcePaths.size(); index++)
				expr = expr.replace(new InputSelection(index),
					new ObjectAccess(nameForPath(sourcePaths.get(index))).withInputExpression(new InputSelection(0)));
			return expr;
		}
		if (generator instanceof SkolemFunctionGenerator) {
			SkolemFunctionGenerator skolemGenerator = (SkolemFunctionGenerator) generator;
			if (generator.getSubGenerators().size() > 0) {
				Collection<EvaluationExpression> expressions =
					new TreeSet<EvaluationExpression>(new Comparator<EvaluationExpression>() {
						@Override
						public int compare(EvaluationExpression o1, EvaluationExpression o2) {
							return o1.toString().compareTo(o2.toString());
						}
					});
				skolemString(skolemGenerator, mappingTask, expressions);
				if (expressions.size() == 1)
					return Iterables.getFirst(expressions, null);
				for (int index = 1; index < expressions.size(); index += 2)
					expressions.add(new ConstantExpression("|"));
				return FunctionUtil.createFunctionCall(CoreFunctions.CONCAT, new ArrayCreation(expressions));
			}
			return new ConstantExpression(skolemGenerator.getName());
			// SkolemFunctionGenerator skolemGenerator = (SkolemFunctionGenerator) generator;
			// StringBuilder result = new StringBuilder();
			//
			// result.append(XQUtility.XQUERY_FUNCTION).append("(\"").append(removeRootLabel(skolemGenerator.getName())).append("\", (");
			// if (generator.getSubGenerators().size() > 0) {
			// String skolemString = new XQSkolemHandler().skolemString(skolemGenerator, mappingTask, false).toString();
			// result.append(skolemString);
			// }
			// result.append("))");
			// return result.toString();
		}
		throw new UnsupportedOperationException();
	}

	public static void skolemString(SkolemFunctionGenerator generator, MappingTask mappingTask,
			Collection<EvaluationExpression> expressions) {
		if (generator.isLeafGenerator()) {
			if (generator.getType() == SkolemFunctionGenerator.STANDARD) {
				if (mappingTask.getConfig().useLocalSkolem()) {
					throw new UnsupportedOperationException();
					// return generateLocalSkolemFunction(generator, mappingTask);
				}
				for (GeneratorWithPath generatorWithPath : generator.getSubGenerators()) {
					VariablePathExpression sourcePath =
						XQUtility.findSourcePath(generator.getTgd().getCoveredCorrespondences(),
							generatorWithPath.getTargetPath());
					expressions.add(new ObjectAccess(SpicyUtil.nameForPath(sourcePath)).withInputExpression(new InputSelection(
						0)));
				}
				// return generateHyperGraphSkolemFunction(generator, mappingTask);
			} else if (generator.getType() == SkolemFunctionGenerator.KEY) {
				for (GeneratorWithPath subGeneratorWithPath : generator.getSubGenerators()) {
					expressions.add(valueForLeaf(subGeneratorWithPath.getGenerator(),
						subGeneratorWithPath.getTargetPath(), generator.getTgd(), mappingTask));
				}
				// return generateSkolemFunctionForKey(generator, mappingTask);
			} else if (generator.getType() == SkolemFunctionGenerator.EGD_BASED) {
				for (GeneratorWithPath subGeneratorWithPath : generator.getSubGenerators()) {
					expressions.add(valueForLeaf(subGeneratorWithPath.getGenerator(),
						subGeneratorWithPath.getTargetPath(), generator.getTgd(), mappingTask));
				}
			} else
				throw new IllegalArgumentException("Incorrect type for leaf generator: " + generator + " - Type: " +
					generator.getType());
		}
		else
			generateSkolemFunctionForIntermediateNode(generator, mappingTask, expressions);
	}

	private static Map<SetAlias, List<GeneratorWithPath>> groupGeneratorsByAlias(SkolemFunctionGenerator skolemFunction) {
		Map<SetAlias, List<GeneratorWithPath>> groups = new HashMap<SetAlias, List<GeneratorWithPath>>();
		for (GeneratorWithPath subGenerator : skolemFunction.getSubGenerators()) {
			SetAlias generatorVariable = subGenerator.getTargetPath().getStartingVariable();
			List<GeneratorWithPath> variableGroup = groups.get(generatorVariable);
			if (variableGroup == null) {
				variableGroup = new ArrayList<GeneratorWithPath>();
				groups.put(generatorVariable, variableGroup);
			}
			variableGroup.add(subGenerator);
		}
		return groups;
	}

	private static ISkolemPart generatePartForAlias(SetAlias variable, List<GeneratorWithPath> subGenerators,
			FORule rule,
			MappingTask mappingTask) {
		ISkolemPart generatorsAppend = new AppendSkolemPart(false, true, "[", "]\"", "-");
		for (GeneratorWithPath subGeneratorWithPath : subGenerators) {
			PathExpression absolutePath = subGeneratorWithPath.getTargetPath().getAbsolutePath();
			ISkolemPart appendForSubGenerator = new AppendSkolemPart(false, true, "", ",\"", ":\",");

			String attributeValue = generateAttributeValue(subGeneratorWithPath, rule, mappingTask);
			appendForSubGenerator.addChild(new StringSkolemPart(attributeValue));
			generatorsAppend.addChild(appendForSubGenerator);
		}
		return generatorsAppend;
	}

	private static String generateAttributeValue(GeneratorWithPath subGeneratorWithPath, FORule rule,
			MappingTask mappingTask) {
		VariablePathExpression targetPath = subGeneratorWithPath.getTargetPath();
		String attributeNameInVariable = "";
		// if (GenerateSQL.hasDifferences(rule)) {
		VariablePathExpression sourcePath = XQUtility.findSourcePath(rule.getCoveredCorrespondences(), targetPath);
		attributeNameInVariable = XQNames.attributeNameInVariable(sourcePath);
		// } else {
		// VariablePathExpression sourcePath = GenerateSQL.findSourcePath(rule.getCoveredCorrespondences(), targetPath);
		// attributeNameInVariable = GenerateSQL.attributeNameWithVariable(sourcePath);
		// }
		return mappingTask.getDBMSHandler().coalesceFunctionWithNull(attributeNameInVariable);
	}

	private static void generateSkolemFunctionForIntermediateNode(
			SkolemFunctionGenerator generator, MappingTask mappingTask, Collection<EvaluationExpression> expressions) {
		// if (generator.isLeafGenerator()) {
		// String skolemString =
		// new it.unibas.spicy.model.algebra.query.operators.sql.CompactSkolemHandler().skolemString(
		// (SkolemFunctionGenerator) generator, mappingTask, false);
		// }
		for (GeneratorWithPath subGeneratorWithPath : generator.getSubGenerators()) {
			// String attributeNameInVariable = XQNames.attributeNameInVariable(subGeneratorWithPath.getTargetPath());
			// VariablePathExpression sourcePath =
			// XQUtility.findSourcePath(generator.getTgd().getCoveredCorrespondences(),
			// subGeneratorWithPath.getTargetPath());
			// if (sourcePath != null)
			expressions.add(new ObjectAccess(nameForPath(subGeneratorWithPath.getTargetPath())));
		}
	}

	public static String nameForPath(VariablePathExpression attributePath) {
		return attributePath.toString();
	}

	public static EvaluationExpression createRelativePathForMetadataNode(VariablePathExpression path,
			INode metatadaNode, InputManager var2Source) {
		throw new UnsupportedOperationException();
		// StringBuilder relativePath = new StringBuilder();
		// INode father = metatadaNode.getFather();
		// INode ancestor = father.getFather();
		// if (ancestor instanceof SetNode) {
		// relativePath.append("xs:string(").append(path.getStartingVariable().toShortStringWithDollar()).append("/@").append(path.getLastStep()).append(")");
		// } else {
		// relativePath.append("xs:string(").append(path.getStartingVariable().toShortStringWithDollar()).append("/");
		// relativePath.append(father.getLabel()).append("/@").append(path.getLastStep()).append(")");
		// }
		// return relativePath.toString();
	}

	public static EvaluationExpression createRelativePathForVirtualAttribute(VariablePathExpression path,
			INode attributeNode,
			InputManager var2Source) {
		INode father = attributeNode.getFather();
		INode ancestor = father.getFather();
		INode ancestorOfAncestor = ancestor.getFather();
		if (father.isVirtual()) {
			if (ancestorOfAncestor instanceof SetNode) {
				return var2Source.getInput(path.getStartingVariable());
			}
			return new ObjectAccess(ancestor.getLabel()).withInputExpression(var2Source.getInput(path.getStartingVariable()));
		}
		return new ObjectAccess(father.getLabel()).withInputExpression(var2Source.getInput(path.getStartingVariable()));
	}

	public static EvaluationExpression createRelativePathForSingleAttribute(VariablePathExpression path,
			INode attributeNode,
			InputManager var2Source) {
		return var2Source.getInput(path.getLastStep());
	}

	//
	// public static EvaluationExpression createRelativePathForCoverage(VariablePathExpression path, Coverage coverage)
	// {
	// StringBuilder relativePath = new StringBuilder();
	// FORule tgd = findCoveringTGD(path, coverage);
	// String variableName = XQNames.NameForApplyFunctions(tgd);
	// relativePath.append(variableName).append("/").append(path.getStartingVariable().toShortString()).append("_").append(path.getLastStep()).append("/text()");
	// return relativePath.toString();
	// }
	//
	public static PathSegmentExpression toSopremoPath(VariablePathExpression path, InputManager var2Source) {
		final List<String> pathSteps = path.getPathSteps();
		PathSegmentExpression expression = var2Source.getInput(path.getStartingVariable());
		for (int index = 1; index < pathSteps.size(); index++) {
			final String step = pathSteps.get(index);
			if (step.startsWith("["))
				expression = new ArrayAccess(Integer.parseInt(step.substring(1, step.length() - 1))).
					withInputExpression(expression);
			else
				expression = new ObjectAccess(step).withInputExpression(expression);
		}
		return expression;
	}

	public static PathSegmentExpression toSopremoPath(PathExpression path) {
		final List<String> pathSteps = path.getPathSteps();
		PathSegmentExpression expression = EvaluationExpression.VALUE;
		for (int index = 3; index < pathSteps.size(); index++) {
			final String step = pathSteps.get(index);
			if (step.startsWith("["))
				expression = new ArrayAccess(Integer.parseInt(step.substring(1, step.length() - 1))).
					withInputExpression(expression);
			else
				expression = new ObjectAccess(step).withInputExpression(expression);
		}
		return expression;
	}

	public static class StreamManager {
		private Map<String, JsonStream> streams = new HashMap<String, JsonStream>();

		public JsonStream getStream(SetAlias alias) {
			return this.getStream(alias.toShortString());
		}

		public JsonStream getStream(String alias) {
			return this.streams.get(alias);
		}

		public List<JsonStream> getStreams(List<SetAlias> alias) {
			ArrayList<JsonStream> streams = new ArrayList<JsonStream>();
			for (SetAlias setAlias : alias)
				streams.add(getStream(setAlias));
			return streams;
		}

		public void put(String name, JsonStream stream) {
			this.streams.put(name, stream);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return this.streams.toString();
		}
	}

	public static class InputManager {
		private Map<String, PathSegmentExpression> aliasesIndex = new HashMap<String, PathSegmentExpression>();

		private Map<String, PathSegmentExpression> fullPaths = new HashMap<String, PathSegmentExpression>();

		public InputManager(List<SetAlias> alias) {
			for (int index = 0; index < alias.size(); index++) {
				SetAlias var = alias.get(index);
				if (var.getBindingPathExpression() == null ||
					var.getBindingPathExpression().getStartingVariable() == null) {
					this.aliasesIndex.put(var.toShortString(), new InputSelection(index));
					this.fullPaths.put(var.toShortString(), new InputSelection(index));
				} else {
					this.aliasesIndex.put(var.toShortString(),
						new ArrayAccess(0).withInputExpression(new InputSelection(index)));
					this.fullPaths.put(var.toShortString(),
						toSopremoPath(var.getAbsoluteBindingPathExpression()));
					while ((var = var.getBindingPathExpression().getStartingVariable()) != null) {
						this.aliasesIndex.put(var.toShortString(),
							new ArrayAccess(ArraySplit.ResultField.WholeValue.ordinal()).
								withInputExpression(new InputSelection(index)));
						this.fullPaths.put(var.toShortString(),
							toSopremoPath(var.getAbsoluteBindingPathExpression()));
					}
				}
			}
		}

		public InputManager(String... alias) {
			for (int index = 0; index < alias.length; index++)
				this.aliasesIndex.put(alias[index], new InputSelection(index));
		}

		public PathSegmentExpression getInput(String id) {
			return this.aliasesIndex.get(id);
		}

		public PathSegmentExpression getInput(SetAlias startingVariable) {
			return getInput(startingVariable.toShortString());
		}

		@Override
		public String toString() {
			return this.aliasesIndex.toString();
		}

		public EvaluationExpression getSubRelationPath(List<SetAlias> variables) {
			for (SetAlias variable : variables)
				if (getInput(variable).getInputExpression() != EvaluationExpression.VALUE)
					return this.fullPaths.get(variable.toShortString());

			return null;
		}
	}

	private static ThreadLocal<SopremoPathToSpicyPath> SopremoPathToSpicyPath =
		new ThreadLocal<SpicyUtil.SopremoPathToSpicyPath>() {
			@Override
			protected SopremoPathToSpicyPath initialValue() {
				return new SopremoPathToSpicyPath();
			};
		};

	public static PathExpression toSpicyPath(EvaluationExpression targetPath, PathExpression rootExpression) {
		ArrayList<String> steps = new ArrayList<String>(rootExpression.getPathSteps());
		steps.addAll(SopremoPathToSpicyPath.get().getSteps(targetPath));
		return new PathExpression(steps);
	}

	public static SopremoFunctionExpression extractTransformation(EvaluationExpression expression,
			PathExpression rootExpression,
			List<PathExpression> sourcePaths) {
		List<List<String>> sources = new ArrayList<List<String>>();
		final EvaluationExpression transform = SopremoPathToSpicyPath.get().extractTransformation(expression, sources);
		for (List<String> sourcePath : sources) {
			sourcePath.addAll(0, rootExpression.getPathSteps());
			sourcePaths.add(new PathExpression(sourcePath));
		}
		return new SopremoFunctionExpression(transform, sourcePaths);
	}

	private static class SopremoPathToSpicyPath extends ReturnlessTreeHandler<EvaluationExpression, List<String>> {
		/**
		 * 
		 */
		private static final String ARRAY_ELEMENT = "0";

		/**
		 * Initializes SpicyUtil.SopremoPathToSpicyPath.
		 */
		public SopremoPathToSpicyPath() {
			put(ObjectAccess.class, new ReturnLessNodeHandler<ObjectAccess, List<String>>() {
				/*
				 * (non-Javadoc)
				 * @see eu.stratosphere.sopremo.tree.ReturnLessNodeHandler#handleNoReturn(java.lang.Object,
				 * java.lang.Object, eu.stratosphere.sopremo.tree.TreeHandler)
				 */
				@Override
				protected void handleNoReturn(ObjectAccess value, List<String> param,
						TreeHandler<Object, Object, List<String>> treeHandler) {
					treeHandler.handle(value.getInputExpression(), param);
					param.add(value.getField());
				}
			});
			put(ArrayAccess.class, new ReturnLessNodeHandler<ArrayAccess, List<String>>() {
				@Override
				protected void handleNoReturn(ArrayAccess value, List<String> param,
						TreeHandler<Object, Object, List<String>> treeHandler) {
					treeHandler.handle(value.getInputExpression(), param);
					param.add(String.valueOf(value.getStartIndex()));
				}
			});
			put(InputSelection.class, new ReturnLessNodeHandler<InputSelection, List<String>>() {
				@Override
				protected void handleNoReturn(InputSelection value, List<String> param,
						TreeHandler<Object, Object, List<String>> treeHandler) {
					param.add(String.valueOf(value.getIndex()));
					param.add(String.valueOf(ARRAY_ELEMENT));
					SopremoPathToSpicyPath.this.pathFinished = true;
				}
			});
		}

		private Map<EvaluationExpression, List<String>> subPaths;

		private boolean pathFinished = false;

		@Override
		protected Object unknownValueType(EvaluationExpression value, List<String> param) {
			if (this.subPaths == null)
				return super.unknownValueType(value, param);

			for (EvaluationExpression child : value) {
				super.handle(child, param);
				if (this.pathFinished)
					this.subPaths.put(child, new ArrayList<String>(param));
				this.pathFinished = false;
				param.clear();
			}
			return null;
		}

		public EvaluationExpression extractTransformation(EvaluationExpression value, List<List<String>> subPaths) {
			List<String> steps = new ArrayList<String>();
			this.subPaths = new HashMap<EvaluationExpression, List<String>>();
			value = value.clone();
			handle(value, steps);
			// no special constructs
			if (this.pathFinished)
				this.subPaths.put(value, steps);
			final Iterator<Entry<EvaluationExpression, List<String>>> iterator = this.subPaths.entrySet().iterator();
			for (int index = 0; iterator.hasNext(); index++) {
				final Entry<EvaluationExpression, List<String>> path = iterator.next();
				if (!path.getValue().isEmpty()) {
					subPaths.add(path.getValue());
					value = value.replace(path.getKey(), new InputSelection(index));
				}
			}
			this.subPaths = null;
			return value;
		}

		public List<String> getSteps(EvaluationExpression value) {
			List<String> steps = new ArrayList<String>();
			handle(value, steps);
			return steps;
		}
	}

	public static INode toSpicySchema(List<EvaluationExpression> schema, String rootLabel) {
		SequenceNode root = new SequenceNode(rootLabel);
		root.setRoot(true);
		for (int index = 0; index < schema.size(); index++) {
			SetNode dataset = new SetNode(String.valueOf(index));
			root.addChild(dataset);
			dataset.addChild(SopremoSchemaToSpicySchema.get().convert(schema.get(index)));
		}
		return root;
	}

	private static ThreadLocal<SopremoSchemaToSpicySchema> SopremoSchemaToSpicySchema =
		new ThreadLocal<SopremoSchemaToSpicySchema>() {
			@Override
			protected SopremoSchemaToSpicySchema initialValue() {
				return new SopremoSchemaToSpicySchema();
			};
		};

	private static class SopremoSchemaToSpicySchema extends TreeHandler<EvaluationExpression, INode, String> {
		/**
		 * Initializes SpicyUtil.SopremoPathToSpicyPath.
		 */
		public SopremoSchemaToSpicySchema() {
			put(ObjectCreation.class, new NodeHandler<ObjectCreation, INode, String>() {
				/*
				 * (non-Javadoc)
				 * @see eu.stratosphere.sopremo.tree.NodeHandler#handle(java.lang.Object, java.lang.Object,
				 * eu.stratosphere.sopremo.tree.TreeHandler)
				 */
				@Override
				public INode handle(ObjectCreation value, String childName,
						TreeHandler<Object, INode, String> treeHandler) {
					SequenceNode sequenceNode = new SequenceNode(childName);
					for (Mapping<?> mapping : value.getMappings()) {
						INode child = treeHandler.handle(mapping.getExpression(),
							cast(mapping, FieldAssignment.class, "").getTarget());
						child.setRequired(true);
						sequenceNode.addChild(child);
					}
					return sequenceNode;
				}
			});
			put(ArrayCreation.class, new NodeHandler<ArrayCreation, INode, String>() {
				/*
				 * (non-Javadoc)
				 * @see eu.stratosphere.sopremo.tree.NodeHandler#handle(java.lang.Object, java.lang.Object,
				 * eu.stratosphere.sopremo.tree.TreeHandler)
				 */
				@Override
				public INode handle(ArrayCreation value, String childName,
						TreeHandler<Object, INode, String> treeHandler) {
					SetNode setNode = new SetNode(childName);
					List<EvaluationExpression> elements = value.getElements();
					for (int index = 0; index < elements.size(); index++)
						setNode.addChild(treeHandler.handle(elements.get(index), String.valueOf(index)));
					return setNode;
				}
			});
			put(EvaluationExpression.ValueExpression.class,
				new NodeHandler<EvaluationExpression.ValueExpression, INode, String>() {
					/*
					 * (non-Javadoc)
					 * @see eu.stratosphere.sopremo.tree.NodeHandler#handle(java.lang.Object, java.lang.Object,
					 * eu.stratosphere.sopremo.tree.TreeHandler)
					 */
					@Override
					public INode handle(ValueExpression value, String childName,
							TreeHandler<Object, INode, String> treeHandler) {
						AttributeNode attributeNode = new AttributeNode(childName);
						attributeNode.addChild(new LeafNode("string"));
						return attributeNode;
					}
				});
		}

		public INode convert(EvaluationExpression schema) {
			return handle(schema, "0");
		}
	}

	public static EvaluationExpression spicyToSopremo(INode schema, IDataSourceProxy dataSource) {
		return SpicySchemaToSopremo.get().convert(schema, dataSource);
	}

	private static ThreadLocal<SpicySchemaToSopremo> SpicySchemaToSopremo = new ThreadLocal<SpicySchemaToSopremo>() {
		@Override
		protected SpicySchemaToSopremo initialValue() {
			return new SpicySchemaToSopremo();
		};
	};

	private static class SpicySchemaToSopremo extends TreeHandler<INode, EvaluationExpression, PathExpression> {
		/**
		 * Initializes SpicyUtil.SopremoPathToSpicyPath.
		 */
		public SpicySchemaToSopremo() {
			put(SequenceNode.class, new NodeHandler<SequenceNode, EvaluationExpression, PathExpression>() {
				@Override
				public EvaluationExpression handle(SequenceNode value, PathExpression path,
						TreeHandler<Object, EvaluationExpression, PathExpression> treeHandler) {
					path.getPathSteps().add(value.getLabel());
					ObjectCreation oc = new ObjectCreation();
					for (INode child : value.getChildren())
						oc.addMapping(child.getLabel(), treeHandler.handle(child, path));
					path.getPathSteps().remove(path.getPathSteps().size() - 1);
					return oc;
				}
			});
			put(SetNode.class, new NodeHandler<SetNode, EvaluationExpression, PathExpression>() {
				@Override
				public EvaluationExpression handle(SetNode value, PathExpression path,
						TreeHandler<Object, EvaluationExpression, PathExpression> treeHandler) {
					path.getPathSteps().add(value.getLabel());
					final String fieldName = nameForPath(SpicySchemaToSopremo.this.gpe.generateRelativePath(
						path, SpicySchemaToSopremo.this.dataSource));
					path.getPathSteps().remove(path.getPathSteps().size() - 1);
					return new ObjectAccess(fieldName).withInputExpression(new ObjectAccess("content"));
				}
			});
			put(AttributeNode.class, new NodeHandler<AttributeNode, EvaluationExpression, PathExpression>() {
				@Override
				public EvaluationExpression handle(AttributeNode value, PathExpression path,
						TreeHandler<Object, EvaluationExpression, PathExpression> treeHandler) {
					path.getPathSteps().add(value.getLabel());
					final String fieldName = nameForPath(SpicySchemaToSopremo.this.gpe.generateRelativePath(
						path, SpicySchemaToSopremo.this.dataSource));
					path.getPathSteps().remove(path.getPathSteps().size() - 1);
					return new ObjectAccess(fieldName).withInputExpression(new ObjectAccess("content"));
				}
			});
		}

		private IDataSourceProxy dataSource;

		private GeneratePathExpression gpe = new GeneratePathExpression();

		public EvaluationExpression convert(INode schema, IDataSourceProxy dataSource) {
			this.dataSource = dataSource;
			return handle(schema, this.gpe.generatePathFromRoot(schema.getFather()));
		}
	}
}
