package org.a5calls.android.a5calls.controller;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.NavUtils;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.a5calls.android.a5calls.AppSingleton;
import org.a5calls.android.a5calls.R;
import org.a5calls.android.a5calls.adapter.OutcomeAdapter;
import org.a5calls.android.a5calls.model.AccountManager;
import org.a5calls.android.a5calls.model.Contact;
import org.a5calls.android.a5calls.model.Issue;
import org.a5calls.android.a5calls.model.Outcome;
import org.a5calls.android.a5calls.net.FiveCallsApi;
import org.a5calls.android.a5calls.util.AnalyticsManager;
import org.a5calls.android.a5calls.util.ScriptReplacements;
import org.a5calls.android.a5calls.util.MarkdownUtil;
import org.a5calls.android.a5calls.view.GridItemDecoration;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

import static org.a5calls.android.a5calls.controller.IssueActivity.KEY_ISSUE;

/**
 * Activity which handles showing a script for a rep and logging calls.
 */
public class RepCallActivity extends AppCompatActivity {
    private static final String TAG = "RepCallActivity";

    public static final String KEY_ADDRESS = "key_address";
    public static final String KEY_LOCATION_NAME = "key_location_name";

    public static final String KEY_ACTIVE_CONTACT_INDEX = "active_contact_index";
    private static final String KEY_LOCAL_OFFICES_EXPANDED = "local_offices_expanded";

    private FiveCallsApi.CallRequestListener mStatusListener;
    private Issue mIssue;
    private int mActiveContactIndex;
    private OutcomeAdapter outcomeAdapter;

    @BindView(R.id.scroll_view) NestedScrollView scrollView;

    @BindView(R.id.rep_info) RelativeLayout repInfoLayout;
    @BindView(R.id.rep_image) ImageView repImage;
    @BindView(R.id.call_this_office) TextView callThisOffice;
    @BindView(R.id.contact_name) TextView contactName;
    @BindView(R.id.phone_number) TextView phoneNumber;
    @BindView(R.id.contact_done_img) ImageButton contactChecked;

    @BindView(R.id.buttons_prompt) TextView buttonsPrompt;
    @BindView(R.id.outcome_list) RecyclerView outcomeList;

    @BindView(R.id.local_office_btn) Button localOfficeButton;
    @BindView(R.id.field_office_section) LinearLayout localOfficeSection;
    @BindView(R.id.field_office_prompt) TextView localOfficePrompt;

    @BindView(R.id.script_section) LinearLayout scriptLayout;
    @BindView(R.id.contact_reason) TextView contactReason;
    @BindView(R.id.call_script) TextView callScript;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String address = getIntent().getStringExtra(KEY_ADDRESS);
        mActiveContactIndex = getIntent().getIntExtra(KEY_ACTIVE_CONTACT_INDEX, 0);
        mIssue = getIntent().getParcelableExtra(KEY_ISSUE);
        if (mIssue == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_rep_call);
        ButterKnife.bind(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(mIssue.name);
        }

        mStatusListener = new FiveCallsApi.CallRequestListener() {
            @Override
            public void onRequestError() {
                returnToIssueWithServerError();
            }

            @Override
            public void onJsonError() {
                returnToIssueWithServerError();
            }

            @Override
            public void onCallCount(int count) {
                // unused
            }

            @Override
            public void onCallReported() {
                // Note: Skips are not reported.
                returnToIssue();
            }
        };
        FiveCallsApi controller = AppSingleton.getInstance(getApplicationContext())
                .getJsonController();
        controller.registerCallRequestListener(mStatusListener);

        // The markdown view gets focus unless we let the scrollview take it back.
        scrollView.setFocusableInTouchMode(true);
        scrollView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        Contact c = mIssue.contacts.get(mActiveContactIndex);
        String script = ScriptReplacements.replacing(
                this,
                mIssue.script,
                c,
                getIntent().getStringExtra(KEY_LOCATION_NAME),
                AccountManager.Instance.getUserName(this)
        );
        MarkdownUtil.setUpScript(callScript, script, getApplicationContext());

        boolean expandLocalOffices = false;
        if (savedInstanceState != null) {
            expandLocalOffices = savedInstanceState.getBoolean(KEY_LOCAL_OFFICES_EXPANDED,
                    false);
        }
        setupContactUi(mActiveContactIndex, expandLocalOffices);

        // If the Issue's Outcome list is somehow empty, use default outcomes
        // See: https://github.com/5calls/android/issues/107
        List<Outcome> issueOutcomes;
        if (mIssue.outcomeModels == null || mIssue.outcomeModels.isEmpty()) {
            issueOutcomes = OutcomeAdapter.DEFAULT_OUTCOMES;
        } else {
            issueOutcomes = mIssue.outcomeModels;
        }

        outcomeAdapter = new OutcomeAdapter(issueOutcomes, new OutcomeAdapter.Callback() {
            @Override
            public void onOutcomeClicked(Outcome outcome) {
                reportEvent(outcome.label);
                reportCall(outcome, address);
            }
        });

        outcomeList.setLayoutManager(
                new GridLayoutManager(this, getSpanCount(RepCallActivity.this)));
        outcomeList.setAdapter(outcomeAdapter);

        int gridPadding = (int) getResources().getDimension(R.dimen.grid_padding);
        outcomeList.addItemDecoration(new GridItemDecoration(gridPadding,
                getSpanCount(RepCallActivity.this)));

        new AnalyticsManager().trackPageview(String.format("/issue/%s/%s/", mIssue.slug,c.id), this);
    }

    @Override
    protected void onDestroy() {
        AppSingleton.getInstance(getApplicationContext()).getJsonController()
                .unregisterCallRequestListener(mStatusListener);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_ISSUE, mIssue);
        outState.putBoolean(KEY_LOCAL_OFFICES_EXPANDED,
                localOfficeSection.getVisibility() == View.VISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                returnToIssue();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reportCall(Outcome outcome, String address) {
        outcomeAdapter.setEnabled(false);
        AppSingleton.getInstance(getApplicationContext()).getDatabaseHelper().addCall(mIssue.id,
                mIssue.name, mIssue.contacts.get(mActiveContactIndex).id,
                mIssue.contacts.get(mActiveContactIndex).name, outcome.status.toString(), address);
        AppSingleton.getInstance(getApplicationContext()).getJsonController().reportCall(
                mIssue.id, mIssue.contacts.get(mActiveContactIndex).id, outcome.label, address);
    }

    private void setupContactUi(int index, boolean expandLocalSection) {
        final Contact contact = mIssue.contacts.get(index);
        contactName.setText(contact.name);

        // Set the reason for contacting this rep, using default text if no reason is provided.
        final String contactReasonText = TextUtils.isEmpty(contact.reason)
                ? getResources().getString(R.string.contact_reason_default)
                : contact.reason;
        contactReason.setText(contactReasonText);

        if (!TextUtils.isEmpty(contact.photoURL)) {
            Glide.with(getApplicationContext())
                    .load(contact.photoURL)
                    .centerCrop()
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.baseline_person_52)
                    .into(repImage);
        }
        phoneNumber.setText(contact.phone);
        Linkify.addLinks(phoneNumber, Linkify.PHONE_NUMBERS);

        if (expandLocalSection) {
            localOfficeButton.setVisibility(View.INVISIBLE);
            expandLocalOfficeSection(contact);
        } else {
            localOfficeSection.setVisibility(View.GONE);
            localOfficeSection.removeViews(1, localOfficeSection.getChildCount() - 1);
            if (contact.field_offices == null || contact.field_offices.length == 0) {
                localOfficeButton.setVisibility(View.GONE);
            } else {
                localOfficeButton.setVisibility(View.VISIBLE);
                localOfficeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        localOfficeButton.setOnClickListener(null);
                        expandLocalOfficeSection(contact);
                    }
                });
            }
        }

        // Show a bit about whether they've been contacted yet
        final List<String> previousCalls = AppSingleton.getInstance(this).getDatabaseHelper()
                .getCallResults(mIssue.id, contact.id);
        if (previousCalls.size() > 0) {
            showContactChecked(previousCalls);
        } else {
            contactChecked.setVisibility(View.GONE);
            contactChecked.setOnClickListener(null);
        }
    }

    private void showContactChecked(final List<String> previousCalls) {
        contactChecked.setVisibility(View.VISIBLE);
        contactChecked.setImageLevel(1);
        contactChecked.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(RepCallActivity.this)
                        .setTitle(R.string.contact_details_dialog_title)
                        .setMessage(getReportedActionsMessage(RepCallActivity.this, previousCalls))
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                    }
                                })
                        .show();
            }
        });
    }

    private String getReportedActionsMessage(Context context, List<String> previousActions) {
        String result = "";

        if (previousActions != null) {
            List<String> displayedActions = new ArrayList<>();
            for (String prev : previousActions) {
                displayedActions.add(Outcome.getDisplayString(context, prev));
            }

            result = TextUtils.join(", ", displayedActions);
        }

        return result;
    }

    private void expandLocalOfficeSection(Contact contact) {
        localOfficeButton.setVisibility(View.INVISIBLE);
        localOfficeSection.setVisibility(View.VISIBLE);
        localOfficePrompt.setText(String.format(getResources().getString(
                R.string.field_office_prompt), contact.name));
        // TODO: Use an adapter or ListView or something. There aren't expected to be
        // so many local offices so this is OK for now.
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < contact.field_offices.length; i++) {
            ViewGroup localOfficeInfo = (ViewGroup) inflater.inflate(
                    R.layout.field_office_list_item, null);
            TextView numberView = (TextView) localOfficeInfo.findViewById(
                    R.id.field_office_number);
            numberView.setText(contact.field_offices[i].phone);
            Linkify.addLinks(numberView, Linkify.PHONE_NUMBERS);
            if (!TextUtils.isEmpty(contact.field_offices[i].city)) {
                ((TextView) localOfficeInfo.findViewById(R.id.field_office_city)).setText(
                        "- " + contact.field_offices[i].city);
            }
            localOfficeSection.addView(localOfficeInfo);
        }
    }

    private void showError(int errorStringId) {
        Snackbar.make(scrollView, errorStringId, Snackbar.LENGTH_SHORT).show();
    }

    private void reportEvent(String event) {
        // Could add analytics here.
    }

    private void returnToIssue() {
        if (isFinishing()) {
            return;
        }
        Intent upIntent = NavUtils.getParentActivityIntent(this);
        if (upIntent == null) {
            return;
        }
        upIntent.putExtra(IssueActivity.KEY_ISSUE, mIssue);
        setResult(IssueActivity.RESULT_OK, upIntent);
        finish();
    }

    private void returnToIssueWithServerError() {
        if (isFinishing()) {
            return;
        }
        Intent upIntent = NavUtils.getParentActivityIntent(this);
        if (upIntent == null) {
            return;
        }
        upIntent.putExtra(IssueActivity.KEY_ISSUE, mIssue);
        setResult(IssueActivity.RESULT_SERVER_ERROR, upIntent);
        finish();
    }

    private int getSpanCount(Activity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        double minButtonWidth = activity.getResources().getDimension(R.dimen.min_button_width);

        return (int) (displayMetrics.widthPixels / minButtonWidth);
    }
}
