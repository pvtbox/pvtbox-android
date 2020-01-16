package net.pvtbox.android.ui.start;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;

import net.pvtbox.android.R;
import net.pvtbox.android.application.Const;
import net.pvtbox.android.ui.BaseActivity;
import net.pvtbox.android.ui.login.LoginActivity;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
*  
*  Pvtbox. Fast and secure file transfer & sync directly across your devices. 
*  Copyright © 2020  Pb Private Cloud Solutions Ltd. 
*  
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*     http://www.apache.org/licenses/LICENSE-2.0
*  
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*  
**/
public class IntroActivity extends BaseActivity {

    @Nullable
    @BindView(R.id.pager)
    ViewPager pager;
    @Nullable
    @BindView(R.id.dots)
    LinearLayout dotsLayout;
    @Nullable
    @BindView(R.id.next)
    Button next;
    @Nullable
    @BindView(R.id.skip)
    Button skip;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.intro_activity);
        ButterKnife.bind(this);
        IntroPagerAdapter adapter = new IntroPagerAdapter(this);
        Objects.requireNonNull(pager).setAdapter(adapter);
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                addBottomDots(position);

                if (position == 2) {
                    Objects.requireNonNull(next).setText(getString(R.string.got_it));
                    Objects.requireNonNull(skip).setVisibility(View.INVISIBLE);
                } else {
                    Objects.requireNonNull(next).setText(getString(R.string.next));
                    Objects.requireNonNull(skip).setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        Objects.requireNonNull(skip).setOnClickListener(v -> openLogin());
        Objects.requireNonNull(next).setOnClickListener(v -> {
            int current = pager.getCurrentItem() + 1;
            if (current < 3) {
                pager.setCurrentItem(current);
            } else {
                openLogin();
            }
        });
        addBottomDots(0);
    }

    private void addBottomDots(int currentPage) {
        TextView[] dots = new TextView[3];

        Objects.requireNonNull(dotsLayout).removeAllViews();
        for (int i = 0; i < dots.length; i++) {
            dots[i] = new TextView(this);
            dots[i].setText(Html.fromHtml("&#8226;"));
            dots[i].setTextSize(35);
            dots[i].setTextColor(ContextCompat.getColor(this, R.color.dots_color));
            dotsLayout.addView(dots[i]);
        }

        dots[currentPage].setTextColor(ContextCompat.getColor(this, R.color.primary_dark));
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(Const.INIT_INTENT, getIntent());
        startActivity(intent);
        finish();
    }
}
