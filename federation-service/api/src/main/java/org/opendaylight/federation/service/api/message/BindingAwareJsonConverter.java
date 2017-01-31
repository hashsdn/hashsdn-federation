/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.federation.service.api.message;

// FIXME - copyright issue !!!
/*
 * Copyright (c) 2015 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import javassist.ClassPool;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.yangtools.sal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.sal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.yangtools.sal.binding.generator.util.JavassistUtils;
import org.opendaylight.yangtools.yang.binding.BindingStreamEventWriter;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.binding.util.BindingReflections;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonParserStream;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

public class BindingAwareJsonConverter {

    private static SchemaContext context;
    private static BindingRuntimeContext bindingContext;
    private static BindingNormalizedNodeCodecRegistry codecRegistry;

    public static final QName TOP_ODL_TTPS_QNAME = QName.create("urn:onf:ttp", "2014-07-11", "opendaylight-ttps");
    public static final YangInstanceIdentifier TOP_ODL_TTPS_PATH = YangInstanceIdentifier.of(TOP_ODL_TTPS_QNAME);

    public static void init(Iterable<? extends YangModuleInfo> moduleInfos) {
        final ModuleInfoBackedContext moduleContext = ModuleInfoBackedContext.create();
        moduleContext.addModuleInfos(moduleInfos);
        context = moduleContext.tryToCreateSchemaContext().get();
        bindingContext = BindingRuntimeContext.create(moduleContext, context);

        final BindingNormalizedNodeCodecRegistry bindingStreamCodecs = new BindingNormalizedNodeCodecRegistry(
                StreamWriterGenerator.create(JavassistUtils.forClassPool(ClassPool.getDefault())));
        bindingStreamCodecs.onBindingRuntimeContextUpdated(bindingContext);
        codecRegistry = bindingStreamCodecs;
    }

    public final SchemaContext getSchemaContext() {
        return context;
    }

    /**
     * Converts a {@link DataObject} to a JSON representation in a string using
     * the relevant YANG schema if it is present. This defaults to using a
     * {@link org.opendaylight.yangtools.yang.model.api.SchemaContextListener}
     * if running an OSGi environment or
     * {@link BindingReflections#loadModuleInfos()} if run while not in an OSGi
     * environment or if the schema isn't available via
     * {@link org.opendaylight.yangtools.yang.model.api.SchemaContextListener}.
     *
     * @param path
     *            {@literal InstanceIdentifier<?>}
     * @param object
     *            DataObject
     * @return String
     */
    public static String jsonStringFromDataObject(InstanceIdentifier<?> path, DataObject object) {
        return jsonStringFromDataObject(path, object, false);
    }

    /**
     * Converts a {@link DataObject} to a JSON representation in a string using
     * the relevant YANG schema if it is present. This defaults to using a
     * {@link org.opendaylight.yangtools.yang.model.api.SchemaContextListener}
     * if running an OSGi environment or
     * {@link BindingReflections#loadModuleInfos()} if run while not in an OSGi
     * environment or if the schema isn't available via
     * {@link org.opendaylight.yangtools.yang.model.api.SchemaContextListener}.
     *
     * @param path
     *            {@literal InstanceIdentifier<?>}
     * @param object
     *            DataObject
     * @param pretty
     *            boolean
     * @return String
     */
    public static String jsonStringFromDataObject(InstanceIdentifier<?> path, DataObject object, boolean pretty) {
        if (object == null) {
            return null;
        }

        final SchemaPath scPath = SchemaPath
                .create(FluentIterable.from(path.getPathArguments()).transform(new Function<PathArgument, QName>() {

                    @Override
                    public QName apply(final PathArgument input) {
                        return BindingReflections.findQName(input.getType());
                    }

                }), true);

        final Writer writer = new StringWriter();
        final NormalizedNodeStreamWriter domWriter;
        if (pretty) {
            domWriter = JSONNormalizedNodeStreamWriter.createExclusiveWriter(JSONCodecFactory.create(context),
                    scPath.getParent(), scPath.getLastComponent().getNamespace(),
                    JsonWriterFactory.createJsonWriter(writer, 2));
        } else {
            domWriter = JSONNormalizedNodeStreamWriter.createExclusiveWriter(JSONCodecFactory.create(context),
                    scPath.getParent(), scPath.getLastComponent().getNamespace(),
                    JsonWriterFactory.createJsonWriter(writer));
        }
        final BindingStreamEventWriter bindingWriter = codecRegistry.newWriter(path, domWriter);

        try {
            codecRegistry.getSerializer(path.getTargetType()).serialize(object, bindingWriter);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        return writer.toString();
    }

    public static Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> guyNormalizedNodeFromDataObject(
            InstanceIdentifier path, DataObject object) {
        Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> result = codecRegistry.toNormalizedNode(path, object);
        return result;
    }

    public static String normalizedNodeToJsonStreamTransformation(final NormalizedNode<?, ?> inputStructure)
            throws IOException {
        // if (path == null) {
        // path = SchemaPath.ROOT;
        // }
        final Writer writer = new StringWriter();
        final NormalizedNodeStreamWriter jsonStream = JSONNormalizedNodeStreamWriter.createExclusiveWriter(
                JSONCodecFactory.create(context), SchemaPath.ROOT, null, JsonWriterFactory.createJsonWriter(writer, 2));
        final NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(jsonStream);
        nodeWriter.write(inputStructure);

        nodeWriter.close();
        return writer.toString();
    }

    public static Set<DataSchemaNode> getAllTheNode(SchemaContext context) {
        Set<DataSchemaNode> nodes = new HashSet<DataSchemaNode>();
        getAllTheNodesHelper(context, nodes);
        return nodes;
    }

    private static void getAllTheNodesHelper(DataNodeContainer dcn, Set<DataSchemaNode> nodes) {
        for (DataSchemaNode dsn : dcn.getChildNodes()) {
            if (dsn instanceof DataNodeContainer) {
                getAllTheNodesHelper((DataNodeContainer) dsn, nodes);
            }
            nodes.add(dsn);
        }
    }

    public static NormalizedNode<?, ?> normalizedNodeFromJsonString(final String inputJson) {
        final NormalizedNodeResult result = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter streamWriter = ImmutableNormalizedNodeStreamWriter.from(result);
        // note: context used to be generated by using loadModules from
        // TestUtils in
        // org.opendaylight.yangtools.yang.data.codec.gson
        final JsonParserStream jsonParser = JsonParserStream.create(streamWriter, context);
        jsonParser.parse(new JsonReader(new StringReader(inputJson)));
        final NormalizedNode<?, ?> transformedInput = result.getResult();
        return transformedInput;
    }

    public DataObject dataObjectFromNormalizedNode(YangInstanceIdentifier identifier, NormalizedNode<?, ?> nn) {
        return codecRegistry.fromNormalizedNode(identifier, nn).getValue();
    }

    public static DataObject dataObjectFromNormalizedNode(QName qname, NormalizedNode<?, ?> nn) {
        return codecRegistry.fromNormalizedNode(YangInstanceIdentifier.of(qname), nn).getValue();
    }

    public DataObject dataObjectFromNormalizedNode(String namespace, String revision, String localName,
            NormalizedNode<?, ?> nn) {
        QName qname = QName.create(namespace, revision, localName);
        return codecRegistry.fromNormalizedNode(YangInstanceIdentifier.of(qname), nn).getValue();
    }

    // public static final QName TOP_ODL_TTPS_QNAME =
    // QName.create("urn:onf:ttp", "2014-07-11", "opendaylight-ttps");
    // public static final YangInstanceIdentifier TOP_ODL_TTPS_PATH =
    // YangInstanceIdentifier.of(TOP_ODL_TTPS_QNAME);

    public DataObject dataObjectFromNormalizedNode(NormalizedNode<?, ?> nn) {
        return codecRegistry.fromNormalizedNode(TOP_ODL_TTPS_PATH, nn).getValue();
    }

    /**
     * DON'T CALL THIS IN PRODUCTION CODE EVER!!! UNTIL IT IS FIXED.
     * Return the {@link DataSchemaNode}.
     *
     * @param context
     *            SchemaContext
     * @param dataObject
     *            DataObject
     * @return DataSchemaNode
     * @deprecated DON'T CALL THIS IN PRODUCTION CODE EVER!!! UNTIL IT IS FIXED
     */
    @Deprecated
    public static final DataSchemaNode getSchemaNodeForDataObject(SchemaContext context, DataObject dataObject) {
        QName qn = BindingReflections.findQName(dataObject.getClass());

        Set<DataSchemaNode> allTheNodes = getAllTheNode(context);

        // TODO: create a map to make this faster!!!!
        for (DataSchemaNode dsn : allTheNodes) {
            if (dsn instanceof DataNodeContainer) {
                allTheNodes.addAll(((DataNodeContainer) dsn).getChildNodes());
            }
            if (dsn.getQName().equals(qn)) {
                return dsn;
            }
        }
        return null;
    }

}
