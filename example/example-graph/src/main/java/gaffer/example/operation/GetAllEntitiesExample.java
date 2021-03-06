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
package gaffer.example.operation;

import gaffer.commonutil.iterable.CloseableIterable;
import gaffer.data.element.Entity;
import gaffer.data.element.function.ElementFilter;
import gaffer.data.elementdefinition.view.View;
import gaffer.data.elementdefinition.view.ViewElementDefinition;
import gaffer.function.simple.filter.IsMoreThan;
import gaffer.operation.impl.get.GetAllEntities;

public class GetAllEntitiesExample extends OperationExample {
    public static void main(final String[] args) {
        new GetAllEntitiesExample().run();
    }

    public GetAllEntitiesExample() {
        super(GetAllEntities.class);
    }

    public void runExamples() {
        getAllEntities();
        getAllEntitiesWithCountGreaterThan2();
    }

    public CloseableIterable<Entity> getAllEntities() {
        // ---------------------------------------------------------
        final GetAllEntities operation = new GetAllEntities();
        // ---------------------------------------------------------

        return runExample(operation);
    }

    public CloseableIterable<Entity> getAllEntitiesWithCountGreaterThan2() {
        // ---------------------------------------------------------
        final GetAllEntities operation = new GetAllEntities.Builder()
                .view(new View.Builder()
                        .entity("entity", new ViewElementDefinition.Builder()
                                .preAggregationFilter(new ElementFilter.Builder()
                                        .select("count")
                                        .execute(new IsMoreThan(2))
                                        .build())
                                .build())
                        .build())
                .build();
        // ---------------------------------------------------------

        return runExample(operation);
    }
}
