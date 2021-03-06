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

package gaffer.accumulostore.key.core.impl.byteEntity;

import gaffer.accumulostore.key.AccumuloKeyPackage;
import gaffer.accumulostore.key.core.impl.CoreKeyBloomFunctor;
import gaffer.store.schema.Schema;

public class ByteEntityKeyPackage extends AccumuloKeyPackage {
    public ByteEntityKeyPackage() {
        setIteratorFactory(new ByteEntityIteratorSettingsFactory());
        setKeyFunctor(new CoreKeyBloomFunctor());
    }

    public ByteEntityKeyPackage(final Schema schema) {
        this();
        setSchema(schema);
    }

    @Override
    public void setSchema(final Schema schema) {
        setRangeFactory(new ByteEntityRangeFactory(schema));
        setKeyConverter(new ByteEntityAccumuloElementConverter(schema));
    }
}
