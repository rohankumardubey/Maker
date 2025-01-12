/*
 *  Copyright (C) 2019 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.maker;

/**
 * Represents a label bound to a {@link MethodMaker method} body.
 *
 * @author Brian S O'Neill
 * @see MethodMaker#label
 */
public interface Label {
    /**
     * Sets the position of the label at the location of the next code instruction.
     *
     * @return this label
     * @throws IllegalStateException if label is already positioned
     */
    Label here();

    /**
     * Generates an unconditional goto statement to this label, which doesn't need to be
     * positioned yet.
     *
     * @return this label
     * @see MethodMaker#goto_
     */
    Label goto_();

    /**
     * Returns the {@code MethodMaker} that this label belongs to.
     */
    MethodMaker methodMaker();
}
