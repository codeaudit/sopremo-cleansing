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
package eu.stratosphere.sopremo.cleansing.record_linkage;

import java.util.List;

import eu.stratosphere.sopremo.expressions.BooleanExpression;
import eu.stratosphere.sopremo.type.BooleanNode;
import eu.stratosphere.sopremo.type.IArrayNode;
import eu.stratosphere.sopremo.type.IJsonNode;
import eu.stratosphere.util.IdentityList;

/**
 * 
 */
final class NodeOrderSelector extends BooleanExpression {
	private final List<IJsonNode> nodes = new IdentityList<IJsonNode>();

	NodeOrderSelector(List<IJsonNode> nodes) {
		this.nodes.addAll(nodes);
	}

	/*
	 * (non-Javadoc)
	 * @see eu.stratosphere.sopremo.expressions.BooleanExpression#evaluate(eu.stratosphere.sopremo.type.IJsonNode)
	 */
	@Override
	public BooleanNode evaluate(IJsonNode pair) {
		@SuppressWarnings("unchecked")
		IArrayNode<IJsonNode> array = (IArrayNode<IJsonNode>) pair;
		return BooleanNode.valueOf(this.nodes.indexOf(array.get(0)) < this.nodes.indexOf(array.get(1)));
	}
}