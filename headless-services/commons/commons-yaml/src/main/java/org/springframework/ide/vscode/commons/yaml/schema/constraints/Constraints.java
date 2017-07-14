/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.schema.constraints;

import static org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems.EXTRA_PROPERTY;
import static org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems.*;
import static org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems.problem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.springframework.ide.vscode.commons.yaml.reconcile.YamlSchemaProblems;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;
import org.springframework.ide.vscode.commons.yaml.schema.SchemaContextAware;
import org.springframework.ide.vscode.commons.yaml.schema.YType;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;

import com.google.common.collect.ImmutableSet;

/**
 * Various static methods for constructing/composing {@link Constraint}s.
 *
 * @author Kris De Volder
 */
public class Constraints {
	
	public static Constraint requireOneOf(String... properties) {
		return new RequireOneOf(properties);
	}

	public static Constraint requireAtMostOneOf(String... properties) {
		return new RequireOneOf(properties).allowFewer(true);
	}
	
	public static Constraint requireAtLeastOneOf(String... properties) {
		return new RequireOneOf(properties).allowMultiple(true);
	}


	static private class RequireOneOf implements Constraint {

		private final String[] _requiredProps;
		private boolean allowFewer = false;
		private boolean allowMultiple = false;

		public RequireOneOf(String[] properties) {
			Assert.isLegal(properties.length>1);
			this._requiredProps = properties;
		}

		public Constraint allowMultiple(boolean b) {
			this.allowMultiple = b;
			return this;
		}

		public Constraint allowFewer(boolean b) {
			this.allowFewer = b;
			return this;
		}

		@Override
		public void verify(DynamicSchemaContext dc, Node parent, Node _map, YType type, IProblemCollector problems) {
			IDocument doc = dc.getDocument();
			Set<String> foundProps = dc.getDefinedProperties();
			if (_map instanceof MappingNode) {
				MappingNode map = (MappingNode) _map;
				List<String> requiredProps = Arrays.asList(_requiredProps);
				long foundPropsCount = requiredProps.stream()
					.filter(foundProps::contains)
					.count();
				if (foundPropsCount==0) {
					if (!allowFewer) {
						problems.accept(missingProperty(
								"One of "+requiredProps+" is required for '"+type+"'", doc, parent, map));
					}
				} else if (foundPropsCount>1 && !allowMultiple) {
					//Mark each of the found keys as a violation:
					for (NodeTuple entry : map.getValue()) {
						String key = NodeUtil.asScalar(entry.getKeyNode());
						if (key!=null && requiredProps.contains(key)) {
							problems.accept(problem(EXTRA_PROPERTY,
									"Only one of "+requiredProps+" should be defined for '"+type+"'",  entry.getKeyNode()));
						}
					}
				}
			}
		}
	}

	public static Constraint deprecated(Function<String, String> messageFormatter, String... _deprecatedNames) {
		Set<String> deprecatedNames = ImmutableSet.copyOf(_deprecatedNames);
		return (DynamicSchemaContext dc, Node parent, Node _map, YType type, IProblemCollector problems) -> {
			if (_map instanceof MappingNode) {
				MappingNode map = (MappingNode) _map;
				for (NodeTuple prop : map.getValue()) {
					Node keyNode = prop.getKeyNode();
					String name = NodeUtil.asScalar(keyNode);
					if (deprecatedNames.contains(name)) {
						problems.accept(YamlSchemaProblems.deprecatedProperty(messageFormatter.apply(name), keyNode));
					}
				}
			}
		};
	}

	/**
	 * Deprecated because you shouldn't need to use this method to create a {@link SchemaContextAware} Constraint.
	 * A Constraint itself is already implicitly aware of the {@link DynamicSchemaContext} (i.e. it already receives
	 * the {@link DynamicSchemaContext} as a parameter to its verify method.
	 * <p>
	 * So instead of using this method to get a hold of the {@link DynamicSchemaContext} simply use the context
	 * passed to your constraint instead.
	 */
	@Deprecated
	public static Constraint schemaContextAware(SchemaContextAware<Constraint> dispatcher) {
		return (DynamicSchemaContext dc, Node parent, Node node, YType type, IProblemCollector problems) -> {
			dispatcher.withContext(dc).verify(dc, parent, node, type, problems);
		};
	}

	public static Constraint mutuallyExclusive(String p1, String p2) {
		return (DynamicSchemaContext dc, Node parent, Node node, YType type, IProblemCollector problems) -> {
			if (node instanceof MappingNode) {
				MappingNode map = (MappingNode) node;
				Set<String> defined = dc.getDefinedProperties();
				if (defined.contains(p1) && defined.contains(p2)) {
					for (NodeTuple tup : map.getValue()) {
						Node keyNode = tup.getKeyNode();
						String key = NodeUtil.asScalar(keyNode);
						if (p1.equals(key) || p1.equals(key)) {
							problems.accept(problem(EXTRA_PROPERTY,
									"Only one of '"+p1+"' and '"+p2+"' should be defined for '"+type+"'", keyNode
							));
						}
					}
				}
			}
		};	
	}

}