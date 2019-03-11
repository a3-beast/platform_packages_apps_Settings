package com.mediatek.faceid;

import android.app.Fragment;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources.Theme;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.settings.core.SubSettingLauncher;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsActivity;
import com.android.settings.SetupWizardUtils;
import java.io.IOException;

public class FaceIdSetupGuide extends SettingsActivity {
    private static final String TAG = "FaceIdSetupGuide";

    @Override
    public Intent getIntent() {
        Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, getFragmentClass().getName());
        return modIntent;
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (FaceIdSetupGuideFragment.class.getName().equals(fragmentName)) return true;
        return false;
    }

    /* package */ Class<? extends Fragment> getFragmentClass() {
        return FaceIdSetupGuideFragment.class;
    }

    @Override
    protected void onApplyThemeResource(Theme theme, int resid, boolean first) {
        resid = SetupWizardUtils.getTheme(getIntent());
        super.onApplyThemeResource(theme, resid, first);
    }

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        LinearLayout layout = (LinearLayout) findViewById(R.id.content_parent);
        layout.setFitsSystemWindows(false);
    }

    public static class FaceIdSetupGuideFragment extends Fragment implements OnClickListener {
        private View mRootView;
        protected ImageView mOuter;
        protected ImageView mInner;
        protected ImageView mPerson;

        // required constructor for fragments
        public FaceIdSetupGuideFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
            mRootView = inflater.inflate(R.layout.face_id_setup_guide, null);
            mOuter = (ImageView) mRootView.findViewById(R.id.image_outer);
            mInner = (ImageView) mRootView.findViewById(R.id.image_inner);
            mPerson = (ImageView) mRootView.findViewById(R.id.image_person);
            TextView guideTitle = (TextView) mRootView.findViewById(R.id.faceid_guide_title);
            guideTitle.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            Button cancel= (Button) mRootView.findViewById(R.id.faceid_cancel_button);
            cancel.setOnClickListener(this);
            Button next = (Button) mRootView.findViewById(R.id.faceid_go_button);
            next.setOnClickListener(this);
            return mRootView;
        }

        public void onClick(View v) {
            switch (v.getId()) {
               case R.id.faceid_go_button:
                   handleNext();
                   break;
               case R.id.faceid_cancel_button:
                   handleCancel();
                   break;
               default:
                   break;
            }
        }

        private void handleNext() {
            //SettingsActivity sa = (SettingsActivity)getActivity();
            //sa.startPreferenceFragment(new FaceIdSetupProgressFragment(), true);
            new SubSettingLauncher(getContext())
                .setDestination(FaceIdSetupProgressFragment.class.getName())
                .setSourceMetricsCategory(MetricsEvent.SECURITY)
                .launch();
        }

        private void handleCancel() {
            getActivity().finish();
        }

        @Override
        public void onResume() {
            super.onResume();
            Activity pActivity = getActivity();
            Animation outerCircleAnim = AnimationUtils.loadAnimation(pActivity.getApplicationContext(), R.anim.faceid_guide_circle_outer);
            mOuter.startAnimation(outerCircleAnim);

            Animation innerCircleAnim = AnimationUtils.loadAnimation(pActivity.getApplicationContext(), R.anim.faceid_guide_circle_inner);
            mInner.startAnimation(innerCircleAnim);

            Animation personAnim_1 = AnimationUtils.loadAnimation(pActivity.getApplicationContext(), R.anim.faceid_guide_person_1);
            Animation personAnim_2 = AnimationUtils.loadAnimation(pActivity.getApplicationContext(), R.anim.faceid_guide_person_2);
            Animation personAnim_3 = AnimationUtils.loadAnimation(pActivity.getApplicationContext(), R.anim.faceid_guide_person_3);
            personAnim_1.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mPerson.startAnimation(personAnim_2);
                }
            });
            personAnim_2.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mPerson.startAnimation(personAnim_3);
                }
            });

            mPerson.startAnimation(personAnim_1);
        }

        @Override
        public void onPause() {
            super.onPause();
            mOuter.clearAnimation();
            mInner.clearAnimation();
            mPerson.clearAnimation();
        }
    }
}
