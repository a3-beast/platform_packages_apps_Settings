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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.os.Handler;

public final class CircleProgressView extends View {

    private final float mSmallSolidRadius;
    private final float mRadiusOffset;
    private final int mToGoColor;
    private final int mFinishedColor;
    private final float mStrokeSize;
    // Redraw this view every 40 milli-second
    private final int mInvalidateViewGap = 40;

    private final Paint mPaint = new Paint();
    private final Paint mFill = new Paint();
    private final RectF mArcRect = new RectF();
    private final Handler mUiHandler = new Handler();
    private Timer mTimer;

    @SuppressWarnings("unused")
    public CircleProgressView(Context context) {
        this(context, null);
    }

    public CircleProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);

        final Resources resources = context.getResources();
        //final float dotDiameter = resources.getDimension(R.dimen.circletimer_dot_size);
        float dotDiameter = 24.0f;

        mSmallSolidRadius = dotDiameter / 2f;
        mStrokeSize = 10.0f;
        mRadiusOffset = Math.max(mStrokeSize, Math.max(dotDiameter, 0));

        mToGoColor = Color.GREEN;
        mFinishedColor = Color.GRAY;

        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);

        mFill.setAntiAlias(true);
        mFill.setColor(mToGoColor);
        mFill.setStyle(Paint.Style.FILL);
    }

    void update(Timer timer) {
        if (mTimer != timer) {
            mTimer = timer;
            postInvalidateOnAnimation();
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mTimer == null) {
            return;
        }

        // Compute the size and location of the circle to be drawn.
        final int xCenter = getWidth() / 2;
        final int yCenter = getHeight() / 2;
        final float radius = Math.min(xCenter, yCenter) - mRadiusOffset;

        // Reset old painting state.
        mPaint.setColor(mToGoColor);
        mPaint.setStrokeWidth(mStrokeSize);

        // If the timer is reset, draw a simple white circle.
        final float redPercent;
        if (mTimer.isReset()) {
            // Draw a complete white circle; no red arc required.
            canvas.drawCircle(xCenter, yCenter, radius, mPaint);

            // Red percent is 0 since no timer progress has been made.
            redPercent = 0;
        } else {
            // Draw a combination of red and white arcs to create a circle.
            mArcRect.top = yCenter - radius;
            mArcRect.bottom = yCenter + radius;
            mArcRect.left = xCenter - radius;
            mArcRect.right = xCenter + radius;

            final float whitePercent = Math.min(1, (float) mTimer.getElapsedTime() / (float) mTimer.getTotalTime());
            redPercent = 1 - whitePercent;

            // Draw a white arc to indicate the amount of timer that remains.
            canvas.drawArc(mArcRect, 270, whitePercent * 360, false, mPaint);

            // Draw a red arc to indicate the amount of timer completed.
            mPaint.setColor(mFinishedColor);
            canvas.drawArc(mArcRect, 270, -redPercent * 360 , false, mPaint);
        }

        // Draw a red dot to indicate current progress through the timer.
        final float dotAngleDegrees = 270 - redPercent * 360;
        final double dotAngleRadians = Math.toRadians(dotAngleDegrees);
        final float dotX = xCenter + (float) (radius * Math.cos(dotAngleRadians));
        final float dotY = yCenter + (float) (radius * Math.sin(dotAngleRadians));
        canvas.drawCircle(dotX, dotY, mSmallSolidRadius, mFill);

        if (mTimer.isRunning()) {
            mUiHandler.postDelayed(new Runnable() {
                public void run() {
                    postInvalidateOnAnimation();
                }
            }, mInvalidateViewGap);
        }
    }
}
