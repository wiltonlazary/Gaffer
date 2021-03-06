/*
 * Copyright 2016 Crown Copyright
 *
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
package gaffer.example.function.filter;


import gaffer.function.FilterFunction;
import gaffer.function.context.ConsumerFunctionContext;
import gaffer.function.simple.filter.And;
import gaffer.function.simple.filter.IsLessThan;
import gaffer.function.simple.filter.IsMoreThan;
import java.util.Arrays;

public class AndExample extends FilterFunctionExample {
    public static void main(final String[] args) {
        new AndExample().run();
    }

    public AndExample() {
        super(And.class);
    }

    public void runExamples() {
        isLessThan3AndIsMoreThan0();
        property1IsLessThan2AndProperty2IsMoreThan2();
    }

    public void isLessThan3AndIsMoreThan0() {
        // ---------------------------------------------------------
        final And function = new And(Arrays.asList(
                new ConsumerFunctionContext.Builder<Integer, FilterFunction>()
                        .select(0) // select first property
                        .execute(new IsLessThan(3))
                        .build(),
                new ConsumerFunctionContext.Builder<Integer, FilterFunction>()
                        .select(0) // select first property
                        .execute(new IsMoreThan(0))
                        .build()));
        // ---------------------------------------------------------

        runExample(function,
                new Object[]{0},
                new Object[]{1},
                new Object[]{2},
                new Object[]{3},
                new Object[]{1L},
                new Object[]{2L}
        );
    }

    public void property1IsLessThan2AndProperty2IsMoreThan2() {
        // ---------------------------------------------------------
        final And function = new And(Arrays.asList(
                new ConsumerFunctionContext.Builder<Integer, FilterFunction>()
                        .select(0) // select first property
                        .execute(new IsLessThan(2))
                        .build(),
                new ConsumerFunctionContext.Builder<Integer, FilterFunction>()
                        .select(1) // select second property
                        .execute(new IsMoreThan(2))
                        .build()));
        // ---------------------------------------------------------

        runExample(function,
                new Object[]{1, 3},
                new Object[]{1, 1},
                new Object[]{3, 3},
                new Object[]{3, 1},
                new Object[]{1L, 3L},
                new Object[]{1}
        );
    }
}
