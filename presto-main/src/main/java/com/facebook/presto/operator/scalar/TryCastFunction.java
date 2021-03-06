/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.operator.scalar;

import com.facebook.presto.metadata.BoundVariables;
import com.facebook.presto.metadata.FunctionKind;
import com.facebook.presto.metadata.FunctionRegistry;
import com.facebook.presto.metadata.Signature;
import com.facebook.presto.metadata.SqlScalarFunction;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.TypeManager;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Primitives;

import java.lang.invoke.MethodHandle;
import java.util.List;

import static com.facebook.presto.metadata.Signature.typeVariable;
import static com.facebook.presto.spi.type.TypeSignature.parseTypeSignature;
import static com.facebook.presto.type.UnknownType.UNKNOWN;
import static java.lang.invoke.MethodHandles.catchException;
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodType.methodType;

public class TryCastFunction
        extends SqlScalarFunction
{
    public static final TryCastFunction TRY_CAST = new TryCastFunction();

    public TryCastFunction()
    {
        super(new Signature(
                "TRY_CAST",
                FunctionKind.SCALAR,
                ImmutableList.of(typeVariable("F"), typeVariable("T")),
                ImmutableList.of(),
                parseTypeSignature("T"),
                ImmutableList.of(parseTypeSignature("F")),
                false));
    }

    @Override
    public boolean isHidden()
    {
        return true;
    }

    @Override
    public boolean isDeterministic()
    {
        return true;
    }

    @Override
    public String getDescription()
    {
        return "";
    }

    @Override
    public ScalarFunctionImplementation specialize(BoundVariables boundVariables, int arity, TypeManager typeManager, FunctionRegistry functionRegistry)
    {
        Type fromType = boundVariables.getTypeVariable("F");
        Type toType = boundVariables.getTypeVariable("T");

        Class<?> returnType = Primitives.wrap(toType.getJavaType());
        List<Boolean> nullableArguments;
        MethodHandle tryCastHandle;

        if (fromType.equals(UNKNOWN)) {
            nullableArguments = ImmutableList.of(true);
            tryCastHandle = dropArguments(constant(returnType, null), 0, Void.class);
        }
        else {
            // the resulting method needs to return a boxed type
            Signature signature = functionRegistry.getCoercion(fromType, toType);
            ScalarFunctionImplementation implementation = functionRegistry.getScalarFunctionImplementation(signature);
            nullableArguments = implementation.getNullableArguments();
            MethodHandle coercion = implementation.getMethodHandle();
            coercion = coercion.asType(methodType(returnType, coercion.type()));

            MethodHandle exceptionHandler = dropArguments(constant(returnType, null), 0, RuntimeException.class);
            tryCastHandle = catchException(coercion, RuntimeException.class, exceptionHandler);
        }

        return new ScalarFunctionImplementation(true, nullableArguments, tryCastHandle, isDeterministic());
    }
}
