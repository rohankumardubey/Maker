/*
 *  Copyright 2019 Cojen.org
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

import org.junit.*;
import static org.junit.Assert.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public class FieldTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(FieldTest.class.getName());
    }

    @Test
    public void cases() throws Exception {
        ClassMaker cm = ClassMaker.begin().public_().sourceFile("FieldTest").final_();
        cm.addConstructor().public_().invokeSuperConstructor();

        cm.addField(String.class, "str").private_().static_().init("hello");
        cm.addField(int.class, "num1").private_();
        cm.addField(double.class, "num2").volatile_();

        FieldMaker fm = cm.addField(float.class, "num3");

        try {
            fm.init(10.0f);
            fail();
        } catch (IllegalStateException e) {
        }

        fm.static_().final_().init(10.1f);

        MethodMaker mm = cm.addMethod(null, "run").public_().final_();

        var strVar = mm.field("str");
        var num1Var = mm.field("num1");
        var num2Var = mm.field("num2");

        var assertVar = mm.var(Assert.class);
        assertVar.invoke("assertEquals", "hello", strVar);
        assertVar.invoke("assertEquals", 0L, num1Var);
        assertVar.invoke("assertEquals", 0.0, num2Var, 0);

        strVar.set("world");
        assertVar.invoke("assertEquals", "world", strVar.get());
        strVar.setPlain("plain world");
        assertVar.invoke("assertEquals", "plain world", strVar.getPlain());
        strVar.setOpaque("opaque world");
        assertVar.invoke("assertEquals", "opaque world", strVar.getOpaque());
        strVar.setRelease("acq/rel world");
        assertVar.invoke("assertEquals", "acq/rel world", strVar.getAcquire());
        strVar.setVolatile("volatile world");
        assertVar.invoke("assertEquals", "volatile world", strVar.getVolatile());

        assertVar.invoke("assertTrue", num1Var.compareAndSet(0, 10));
        assertVar.invoke("assertFalse", num1Var.compareAndSet(0, 20));
        assertVar.invoke("assertTrue", num1Var.weakCompareAndSet(10, 20));
        assertVar.invoke("assertTrue", num1Var.weakCompareAndSetPlain(20, 10));
        assertVar.invoke("assertTrue", num1Var.weakCompareAndSetAcquire(10, 15));
        assertVar.invoke("assertTrue", num1Var.weakCompareAndSetRelease(15, 20));
        assertVar.invoke("assertEquals", 20, num1Var.compareAndExchange(15, 30));
        assertVar.invoke("assertEquals", 20, num1Var.compareAndExchange(20, 30));
        assertVar.invoke("assertEquals", 30, num1Var.compareAndExchangeAcquire(30, 40));
        assertVar.invoke("assertEquals", 40, num1Var.compareAndExchangeRelease(40, 50));

        assertVar.invoke("assertEquals", 0.0, num2Var.getAndSet(1.2), 0.0);
        assertVar.invoke("assertEquals", 1.2, num2Var.getAndSetAcquire(1.4), 0.0);
        assertVar.invoke("assertEquals", 1.4, num2Var.getAndSetRelease(-1.6), 0.0);

        assertVar.invoke("assertEquals", 50, num1Var.getAndAdd(1));
        assertVar.invoke("assertEquals", 51, num1Var.getAndAddAcquire(1));
        assertVar.invoke("assertEquals", 52, num1Var.getAndAddRelease(1));
        assertVar.invoke("assertEquals", 53, num1Var);

        num1Var.set(0b1000);
        assertVar.invoke("assertEquals", 0b1000, num1Var.getAndBitwiseOr(0b001));
        assertVar.invoke("assertEquals", 0b1001, num1Var.getAndBitwiseOrAcquire(0b010));
        assertVar.invoke("assertEquals", 0b1011, num1Var.getAndBitwiseOrRelease(0b100));
        assertVar.invoke("assertEquals", 0b1111, num1Var.getAndBitwiseAnd(~0b001));
        assertVar.invoke("assertEquals", 0b1110, num1Var.getAndBitwiseAndAcquire(~0b010));
        assertVar.invoke("assertEquals", 0b1100, num1Var.getAndBitwiseAndRelease(~0b100));
        assertVar.invoke("assertEquals", 0b1000, num1Var.getAndBitwiseXor(0b1000));
        assertVar.invoke("assertEquals", 0b0000, num1Var.getAndBitwiseXorAcquire(0b1001));
        assertVar.invoke("assertEquals", 0b1001, num1Var.getAndBitwiseXorRelease(0b1001));
        assertVar.invoke("assertEquals", 0, num1Var);

        assertVar.invoke("assertEquals", 10.1f, mm.field("num3"), 0.0f);

        num1Var.inc(10);
        assertVar.invoke("assertEquals", 10, num1Var.getVolatile());
        num1Var.setVolatile(20);
        assertVar.invoke("assertEquals", 20, num1Var.getPlain());

        assertVar.invoke("assertFalse", strVar.compareAndSet("yo", "yo!"));
        assertVar.invoke("assertEquals", "volatile world", strVar.getAndSet("yo!"));
        assertVar.invoke("assertEquals", "yo!", strVar);

        mm.return_();

        var clazz = cm.finish();
        var obj = clazz.getConstructor().newInstance();
        clazz.getMethod("run").invoke(obj);
    }
}