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

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.TreeMap;

/**
 * The wonderful StackMapTable attribute!
 *
 * @author Brian S O'Neill
 */
class StackMapTable extends Attribute {
    private final Frame mInitFrame;
    private TreeMap<Integer, Frame> mFrames;
    private byte[] mFinished;

    /**
     * @param initCodes can be null
     */
    StackMapTable(ConstantPool cp, int[] initCodes) {
        super(cp, "StackMapTable");
        mInitFrame = new Frame(Integer.MIN_VALUE, initCodes, null);
    }

    /**
     * Add a frame entry to the table.
     *
     * @param address code address the new frame refers to; pass -1 if not known yet
     * @param localCodes can be null
     * @param stackCodes can be null
     */
    Frame add(int address, int[] localCodes, int[] stackCodes) {
        if (mFrames == null) {
            mFrames = new TreeMap<>();
        }

        Integer key = address;

        if (address >= 0 && mFrames != null) {
            Frame frame = mFrames.get(key);
            if (frame != null) {
                frame.verify(localCodes, stackCodes);
                return frame;
            }
        }

        Frame frame = new Frame(address, localCodes, stackCodes);

        if (address >= 0) {
            mFrames.put(key, frame);
        }

        return frame;
    }

    /**
     * @return false if table is empty and should not be written
     */
    boolean finish() {
        if (mFrames == null || mFrames.isEmpty()) {
            return false;
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);

        try {
            dout.writeShort(mFrames.size());
            Frame prev = mInitFrame;
            for (Frame frame : mFrames.values()) {
                frame.writeTo(prev, dout);
                prev = frame;
            }
            dout.flush();
            mFinished = bout.toByteArray();
        } catch (IOException e) {
            // Not expected.
            throw new IllegalStateException(e);
        }

        return true;
    }

    @Override
    int length() {
        return mFinished.length;
    }

    @Override
    void writeDataTo(DataOutput dout) throws IOException {
        dout.write(mFinished);
    }

    private void add(Frame frame) {
        Integer key = frame.mAddress;
        Frame existing = mFrames.get(key);
        if (existing != null) {
            existing.verify(frame);
        } else {
            mFrames.put(key, frame);
        }
    }

    static class Frame implements Comparable<Frame> {
        int mAddress;
        final int[] mLocalCodes;
        final int[] mStackCodes;

        Frame(int address, int[] localCodes, int[] stackCodes) {
            mAddress = address;
            mLocalCodes = localCodes;
            mStackCodes = stackCodes;
        }

        /**
         * @return stack size
         */
        public int setAddress(StackMapTable table, int address) {
            if (mAddress < 0) {
                mAddress = address;
                table.add(this);
            } else if (address != mAddress) {
                throw new IllegalStateException("Frame address changed");
            }
            return mStackCodes == null ? 0 : mStackCodes.length;
        }

        @Override
        public int compareTo(Frame other) {
            return Integer.compare(mAddress, other.mAddress);
        }

        void verify(Frame other) {
            verify(other.mLocalCodes, other.mStackCodes);
        }

        void verify(int[] localCodes, int[] stackCodes) {
            verify(localCodes, localCodes == null ? 0 : localCodes.length,
                   stackCodes, stackCodes == null ? 0 : stackCodes.length);
        }

        void verify(int[] localCodes, int localLen, int[] stackCodes, int stackLen) {
            verify("variables", mLocalCodes, localCodes, localLen);
            verify("stack", mStackCodes, stackCodes, stackLen);
        }

        private static void verify(String which, int[] expect, int[] actual, int actualLen) {
            if (actual == null || actualLen == 0) {
                if (expect == null || expect.length == 0) {
                    return;
                }
            } else if (expect != null && expect.length == actualLen) {
                check: {
                    for (int i=0; i<actualLen; i++) {
                        if (actual[i] != expect[i]) {
                            break check;
                        }
                    }
                    return;
                }
            }

            throw new IllegalStateException("Mismatched " + which + " at branch target");
        }

        void writeTo(Frame prev, DataOutput dout) throws IOException {
            int offsetDelta;
            if (prev.mAddress < 0) {
                if (prev.mAddress == Integer.MIN_VALUE) {
                    // Initial offset.
                    offsetDelta = mAddress;
                } else {
                    throw new IllegalStateException("Unpositioned frame");
                }
            } else {
                offsetDelta = mAddress - prev.mAddress - 1;
            }

            if (mStackCodes == null || mStackCodes.length <= 1) {
                int localsDiff = diff(prev.mLocalCodes, mLocalCodes);
                if (localsDiff == 0) {
                    if (offsetDelta < 64) {
                        if (mStackCodes == null || mStackCodes.length == 0) {
                            // same_frame
                            dout.writeByte(offsetDelta);
                        } else {
                            // same_locals_1_stack_item_frame
                            dout.writeByte(offsetDelta + 64);
                            writeCode(dout, mStackCodes[0]);
                        }
                    } else {
                        if (mStackCodes == null || mStackCodes.length == 0) {
                            // same_frame_extended
                            dout.writeByte(251);
                            dout.writeShort(offsetDelta);
                        } else {
                            // same_locals_1_stack_item_frame_extended
                            dout.writeByte(247);
                            dout.writeShort(offsetDelta);
                            writeCode(dout, mStackCodes[0]);
                        }
                    }
                    return;
                } else if (localsDiff >= -3 && localsDiff <= 3) {
                    if (mStackCodes == null || mStackCodes.length == 0) {
                        // chop_frame or append_frame
                        dout.writeByte(251 + localsDiff);
                        dout.writeShort(offsetDelta);
                        if (localsDiff > 0) {
                            int i = mLocalCodes.length - localsDiff;
                            for (; i < mLocalCodes.length; i++) {
                                writeCode(dout, mLocalCodes[i]);
                            }
                        }
                        return;
                    }
                }
            }

            // full_frame
            dout.writeByte(255);
            dout.writeShort(offsetDelta);
            writeCodes(dout, mLocalCodes);
            writeCodes(dout, mStackCodes);
        }

        private static void writeCodes(DataOutput dout, int[] codes) throws IOException {
            if (codes == null) {
                dout.writeShort(0);
            } else {
                dout.writeShort(codes.length);
                for (int code : codes) {
                    writeCode(dout, code);
                }
            }
        }

        private static void writeCode(DataOutput dout, int code) throws IOException {
            int smCode = code & 0xff;
            dout.writeByte(smCode);
            if (smCode == Type.SM_OBJECT || smCode == Type.SM_UNINIT) {
                dout.writeShort(code >> 8);
            }
        }

        /**
         * @return MIN_VALUE if mismatched; 0 if the same, -n if chopped, +n if appended
         */
        private static int diff(int[] from, int[] to) {
            if (from == null || from.length == 0) {
                return to == null ? 0 : to.length;
            }
            if (to == null || to.length == 0) {
                return from == null ? 0 : -from.length;
            }
            int mismatch = Arrays.mismatch(from, to);
            if (mismatch < 0) {
                return 0;
            }
            if (mismatch >= from.length || mismatch >= to.length) {
                return to.length - from.length;
            }
            return Integer.MIN_VALUE;
        }
    }
}