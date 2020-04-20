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

import java.lang.invoke.VarHandle;

/**
 * Represents a field accessible by the body of a {@link MethodMaker method}.
 *
 * @author Brian S O'Neill
 */
public interface Field extends Variable {
    @Override
    public default Field name(String name) {
        throw new IllegalStateException("Already named");
    }

    @Override
    public Field set(Object value);

    /**
     * Access the field using plain mode, ignoring any volatile declaration.
     *
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#get
     */
    public Variable getPlain();

    /**
     * Set the field using plain mode, ignoring any volatile declaration.
     *
     * @see VarHandle#set
     */
    public void setPlain(Object value);

    /**
     * Access the field using opaque mode.
     *
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getOpaque
     */
    public Variable getOpaque();

    /**
     * Set the field using opaque mode.
     *
     * @see VarHandle#setOpaque
     */
    public void setOpaque(Object value);

    /**
     * Access the field using acquire mode.
     *
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAcquire
     */
    public Variable getAcquire();

    /**
     * Set the field using release mode.
     *
     * @see VarHandle#setRelease
     */
    public void setRelease(Object value);

    /**
     * Access the field as if it was declared volatile.
     *
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getVolatile
     */
    public Variable getVolatile();

    /**
     * Set the field as if it was declared volatile.
     *
     * @see VarHandle#setVolatile
     */
    public void setVolatile(Object value);

    /**
     * @return the result in a new boolean variable
     * @see VarHandle#compareAndSet
     */
    public Variable compareAndSet(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#compareAndExchange
     */
    public Variable compareAndExchange(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#compareAndExchangeAcquire
     */
    public Variable compareAndExchangeAcquire(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#compareAndExchangeRelease
     */
    public Variable compareAndExchangeRelease(Object expectedValue, Object newValue);

    /**
     * @return the result in a new boolean variable
     * @see VarHandle#weakCompareAndSetPlain
     */
    public Variable weakCompareAndSetPlain(Object expectedValue, Object newValue);

    /**
     * @return the result in a new boolean variable
     * @see VarHandle#weakCompareAndSet
     */
    public Variable weakCompareAndSet(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#weakCompareAndSetAcquire
     */
    public Variable weakCompareAndSetAcquire(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#weakCompareAndSetRelease
     */
    public Variable weakCompareAndSetRelease(Object expectedValue, Object newValue);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndSet
     */
    public Variable getAndSet(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndSetAcquire
     */
    public Variable getAndSetAcquire(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndSetRelease
     */
    public Variable getAndSetRelease(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndAdd
     */
    public Variable getAndAdd(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndAddAcquire
     */
    public Variable getAndAddAcquire(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndAddRelease
     */
    public Variable getAndAddRelease(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseOr
     */
    public Variable getAndBitwiseOr(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseOrAcquire
     */
    public Variable getAndBitwiseOrAcquire(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseOrRelease
     */
    public Variable getAndBitwiseOrRelease(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseAnd
     */
    public Variable getAndBitwiseAnd(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseAndAcquire
     */
    public Variable getAndBitwiseAndAcquire(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseAndRelease
     */
    public Variable getAndBitwiseAndRelease(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseXor
     */
    public Variable getAndBitwiseXor(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseXorAcquire
     */
    public Variable getAndBitwiseXorAcquire(Object value);

    /**
     * @return the result in a new variable, with the same type as this one
     * @see VarHandle#getAndBitwiseXorRelease
     */
    public Variable getAndBitwiseXorRelease(Object value);
}