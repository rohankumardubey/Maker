/*
 *  Copyright 2020 Cojen.org
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

import java.lang.invoke.*;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests for various illegal usage exceptions.
 *
 * @author Brian S O'Neill
 */
public class UsageTest {
    public static void main(String[] args) throws Exception {
        org.junit.runner.JUnitCore.main(UsageTest.class.getName());
    }

    private ClassMaker mClassMaker;

    @Before
    public void setup() {
        mClassMaker = ClassMaker.begin();
    }

    @Test
    public void changeExtend() {
        mClassMaker.extend(UsageTest.class);
        try {
            mClassMaker.extend(UsageTest.class);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Super");
        }
    }

    @Test
    public void changeAutoExtend() {
        MethodMaker mm = mClassMaker.addConstructor();
        mm.invokeSuperConstructor();

        try {
            mClassMaker.extend(UsageTest.class);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Super");
        }
    }

    @Test
    public void endReached() {
        MethodMaker mm = mClassMaker.addMethod(int.class, "test");
        try {
            mClassMaker.finish();
            fail();
        } catch (IllegalStateException e) {
            check(e, "End reached");
        }
    }

    @Test
    public void unpositioned() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        Label a = mm.label();
        mm.goto_(a);
        try {
            mClassMaker.finish();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Unpositioned");
        }
    }

    @Test
    public void noThis() {
        MethodMaker mm = mClassMaker.addMethod(null, "test").static_();
        try {
            mm.this_();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not an instance");
        }
    }

    @Test
    public void illegalParam() {
        MethodMaker mm = mClassMaker.addMethod(null, "test").static_();
        try {
            mm.param(-1);
            fail();
        } catch (IndexOutOfBoundsException e) {
        }
        try {
            mm.param(10);
            fail();
        } catch (IndexOutOfBoundsException e) {
        }
    }

    @Test
    public void returnFail() {
        MethodMaker mm = mClassMaker.addMethod(int.class, "test").static_();
        try {
            mm.return_();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Must return a value");
        }
    }

    @Test
    public void returnFail2() {
        MethodMaker mm = mClassMaker.addMethod(void.class, "test").static_();
        try {
            mm.return_(3);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Must return void");
        }
    }

    @Test
    public void returnFail3() {
        MethodMaker mm = mClassMaker.addMethod(int.class, "test").static_();
        try {
            mm.return_("hello");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Automatic conversion");
        }
    }

    @Test
    public void wrongField() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.field("missing");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Field not found");
        }
    }

    @Test
    public void notConstructor() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.invokeThisConstructor();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not defining");
        }
    }

    @Test
    public void paramCount() throws Exception {
        MethodHandle handle = MethodHandles.lookup()
            .findStatic(UsageTest.class, "check",
                        MethodType.methodType(void.class, Exception.class, String.class));

        MethodMaker mm = mClassMaker.addMethod(null, "test");

        try {
            mm.invoke(handle, 3);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Wrong number");
        }
    }

    @Test
    public void paramCount2() throws Exception {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        var bootstrap = mm.var(UsageTest.class).bootstrap("boot");

        try {
            bootstrap.invoke(void.class, "test", new Object[] {int.class}, 1, 2);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Mismatched");
        }
    }

    public static CallSite boot(MethodHandles.Lookup caller, String name, MethodType type) {
        throw null;
    }

    @Test
    public void dimensions() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.new_(int[].class);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "At least one");
        }
        try {
            mm.new_(long[].class, 10, 10);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Too many");
        }
    }

    @Test
    public void tooMuchCode() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        var a = mm.var(int.class);
        for (int i=0; i<100_000; i++) {
            a.inc(1);
        }
        try {
            mClassMaker.finish();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Code limit");
        }
    }

    @Test
    public void unknownVar() {
        MethodMaker mm1 = mClassMaker.addMethod(null, "test1");
        MethodMaker mm2 = mClassMaker.addMethod(null, "test2");
        var a = mm1.var(int.class).set(0);
        var b = mm2.var(int.class).set(0);
        try {
            a.set(b);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Unknown variable");
        }
    }

    @Test
    public void storeNull() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).set(null);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Cannot store null");
        }
    }

    @Test
    public void unsupportedConstant() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(Object.class).set(new java.util.ArrayList());
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Unsupported constant");
        }
        try {
            mm.var(Object.class).set(new java.math.BigInteger("123"));
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Unsupported constant");
        }
    }

    @Test
    public void unmodifiable() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.class_().set(null);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Unmodifiable");
        }
    }

    @Test
    public void nullMath() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).set(1).add(null);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Cannot add by null");
        }
        try {
            mm.var(int.class).set(1).shr(null);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Cannot shift by null");
        }
    }

    @Test
    public void wrongMath() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(double.class).set(1).shr(1);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot shift to a non-integer");
        }
        try {
            mm.var(String.class).set("hello").add(1);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot add to a non-numeric");
        }
        try {
            mm.var(String.class).set("hello").ushr(1);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot shift to a non-numeric");
        }
    }

    @Test
    public void unknownLabel() {
        MethodMaker mm1 = mClassMaker.addMethod(null, "test1");
        MethodMaker mm2 = mClassMaker.addMethod(null, "test2");
        try {
            mm1.goto_(null);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Label is null");
        }
        Label a = mm1.label().here();
        try {
            mm2.goto_(a);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Unknown label");
        }
        try {
            mm2.goto_(new Label() { public Label here() {return this;} });
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Unknown label");
        }
        try {
            a.here();
            fail();
        } catch (IllegalStateException e) {
            // Cannot position again.
        }
    }

    @Test
    public void wrongComparison() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        Label a = mm.label().here();
        try {
            mm.var(int.class).ifEq(null, a);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Cannot compare");
        }
        try {
            mm.var(int.class).ifEq("hello", a);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Incomparable");
        }
    }

    @Test
    public void switchFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        Label a = mm.label().here();
        Label b = mm.label().here();
        try {
            mm.var(int.class).switch_(a, new int[]{1}, a, b);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Number of cases");
        }
        try {
            mm.var(int.class).switch_(a, new int[]{1, 1}, a, b);
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Duplicate");
        }
    }

    @Test
    public void castFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).instanceOf(String.class);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not an object");
        }
        try {
            mm.var(int.class).cast(String.class);
            fail();
        } catch (IllegalStateException e) {
            check(e, "Unsupported");
        }
    }

    @Test
    public void arrayFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).set(1).alength();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not an array");
        }
    }

    @Test
    public void throwFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).throw_();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Cannot throw");
        }
    }

    @Test
    public void renameFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).name("foo").name("bar");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Already named");
        }

        try {
            mm.class_().name("foo");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Already named");
        }

        mClassMaker.addField(int.class, "foo");
        var field = mm.field("foo");
        try {
            field.name("bar");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Already named");
        }
    }

    @Test
    public void monitorFail() {
        MethodMaker mm = mClassMaker.addMethod(null, "test");
        try {
            mm.var(int.class).monitorEnter();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Not an object");
        }
        mClassMaker.addField(String.class, "foo");
        var field = mm.field("foo");
        try {
            field.monitorEnter();
            fail();
        } catch (IllegalStateException e) {
        }
        try {
            field.monitorExit();
            fail();
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void doubleFinish() {
        mClassMaker.finish();
        try {
            mClassMaker.finish();
            fail();
        } catch (IllegalStateException e) {
            check(e, "Class definition");
        }
    }

    @Test
    public void doubleField() {
        mClassMaker.addField(int.class, "foo");
        try {
            mClassMaker.addField(String.class, "foo");
            fail();
        } catch (IllegalStateException e) {
            check(e, "Field is");
        }
    }

    @Test
    public void wrongMethod() {
        try {
            mClassMaker.addMethod(null, "<clinit>");
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Use the");
        }
        try {
            mClassMaker.addMethod(null, "<init>");
            fail();
        } catch (IllegalArgumentException e) {
            check(e, "Use the");
        }
    }

    @Test
    public void noLookup() {
        try {
            mClassMaker.finishHidden();
            fail();
        } catch (IllegalStateException e) {
            check(e, "No lookup");
        }
    }

    private static void check(Exception e, String message) {
        String actual = e.getMessage();
        assertTrue(message + "; " + actual, actual.startsWith(message));
    }
}
