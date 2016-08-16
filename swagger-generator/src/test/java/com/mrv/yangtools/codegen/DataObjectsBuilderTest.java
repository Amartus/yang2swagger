package com.mrv.yangtools.codegen;

import com.mrv.yangtools.codegen.impl.DataNodeHelper;
import com.mrv.yangtools.codegen.impl.OptimizingDataObjectBuilder;
import com.mrv.yangtools.codegen.impl.UnpackingDataObjectsBuilder;
import com.mrv.yangtools.test.utils.ContextUtils;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective.ListEffectiveStatementImpl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
/**
 * @author bartosz.michalik@amartus.com
 */
@RunWith(MockitoJUnitRunner.class)
public class DataObjectsBuilderTest {

    @Mock
    private Swagger swagger;
    private static SchemaContext ctx;
    private static Module groupings;

    @BeforeClass
    public static void initCtx() throws ReactorException {
        ctx = ContextUtils.getFromClasspath(p -> p.getFileName().toString().equals("with-groupings.yang"));
        groupings = ctx.getModules().iterator().next();
    }

    @Test
    public void testAddModelGroupings() throws Exception {
        //having
        UnpackingDataObjectsBuilder builder = new UnpackingDataObjectsBuilder(ctx, swagger);
        SchemaNode c1 = DataNodeHelper.stream(groupings).filter(n -> n.getQName().getLocalName().equals("c1")).findFirst().orElseThrow(IllegalArgumentException::new);
        //when
        builder.processModule(groupings);
        builder.addModel((ListEffectiveStatementImpl) c1);
        //then
        verify(swagger).addDefinition(eq("C1"), any(Model.class));
    }

    @Test
    public void testNameGroupingsUnpacking() throws Exception {
        //having
        UnpackingDataObjectsBuilder builder = new UnpackingDataObjectsBuilder(ctx, swagger);
        builder.processModule(groupings);
        //when & then
        assertTrue(namesMeetNodes(builder,
                x -> x.getQName().getLocalName().equals("g2-l1"), new HashSet<>(
                        Arrays.asList("G2L1")
                )));
    }

    @Test
    public void testNameGroupingsOptimizing() throws Exception {
        //having
        DataObjectBuilder builder = new OptimizingDataObjectBuilder(ctx, swagger);
        builder.processModule(groupings);
        //when & then
        assertTrue(namesMeetNodes(builder,
                x -> x.getQName().getLocalName().equals("g2-l1"), new HashSet<>(
                        Arrays.asList("G3")
                )));

    }

    protected boolean namesMeetNodes(DataObjectRepo builder, Function<SchemaNode, Boolean> considerNode, Set<String> requiredNames) {
        return ! DataNodeHelper.stream(groupings).filter(considerNode::apply)
                .map(builder::getName).filter(m -> !requiredNames.contains(m)).findFirst().isPresent();
    }
}