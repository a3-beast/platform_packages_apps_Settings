/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 *
 * MediaTek Inc. (C) 2018. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

package com.mediatek.faceid;

import android.os.SystemClock;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TimerTask;

public final class Timer {

    public static final int RESET = 0;
    public static final int RUNNING = 1;
    public static final int PAUSED = 2;
    public static final int EXPIRED = 3;

    private int mCurrentState;
    private long mLength;
    private long mTotalLength;
    private long mRemainingTime;
    private long mLastStartTime;
    private TimerTask mTimeoutTask;
    private java.util.Timer mTimeoutTick;

    Timer(int state, long length) {
        mCurrentState = state;
        mLength = length;
        mTotalLength = length;
        mRemainingTime = length;
        if (state == RUNNING) {
            mLastStartTime = SystemClock.elapsedRealtime();
        } else {
            mLastStartTime = Long.MIN_VALUE;
        }
    }

    Timer(int state, long length, TimerTask timeoutTask) {
        this(state, length);
        mTimeoutTask = timeoutTask;
    }

    public boolean isReset() {
        return mCurrentState == RESET;
    }

    public boolean isRunning() {
        return mCurrentState == RUNNING;
    }

    public long getElapsedTime() {
        return mTotalLength - getToGoTime();
    }

    public long getTotalTime() {
        return mTotalLength;
    }

    public long getToGoTime() {
        long remainingTime = 0;
        if (mCurrentState == RESET) {
            remainingTime = mRemainingTime;
        } else {
            final long timeSinceStart = SystemClock.elapsedRealtime() - mLastStartTime;
            remainingTime = mRemainingTime - Math.max(0, timeSinceStart);
        }
        return remainingTime;
    }

    Timer start() {
        if (mCurrentState == RUNNING) {
            return this;
        }

        if (mTimeoutTask != null) {
            mTimeoutTick = new java.util.Timer();
            mTimeoutTick.schedule(mTimeoutTask, mLength);
        }
        mCurrentState = RUNNING;
        mLastStartTime = SystemClock.elapsedRealtime();
        return this;
    }

    Timer reset() {
        if (mCurrentState == RESET) {
            return this;
        }
        if (mTimeoutTick != null) {
            mTimeoutTick.cancel();
        }
        mCurrentState = RESET;
        return this;
    }
}
