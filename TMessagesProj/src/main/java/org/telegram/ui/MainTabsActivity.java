package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.Premium.LimitReachedBottomSheet.TYPE_ACCOUNTS;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MainTabsOrder;
import org.telegram.messenger.MangoSettings;
import org.telegram.messenger.MessageActionSettings;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.RenderNodeWithHash;
import org.telegram.ui.Components.blur3.capture.IBlur3Hash;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.Collections;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class MainTabsActivity extends ViewPagerActivity implements NotificationCenter.NotificationCenterDelegate, FactorAnimator.Target {
    public static final int TABS_COUNT = 8;
    private static final int POSITION_CONTACTS = 0;
    private static final int POSITION_CHANNEL_FEED = 1;
    private static final int POSITION_UNREAD = 2;
    private static final int POSITION_CHATS = 3;
    private static final int POSITION_SETTINGS = 4;
    private static final int POSITION_PROFILE = 5;
    private static final int POSITION_MESSAGE_CENTER = 6;
    private static final int POSITION_UNIFIED_INBOX = 7;

    private static final int INDEX_CONTACTS = 0;
    private static final int INDEX_CHANNEL_FEED = 1;
    private static final int INDEX_UNREAD = 2;
    private static final int INDEX_CHATS = 3;
    private static final int INDEX_SETTINGS = 4;
    private static final int INDEX_PROFILE = 5;
    private static final int INDEX_MESSAGE_CENTER = 6;
    private static final int INDEX_UNIFIED_INBOX = 7;
    private static final int TABS_WIDTH_TWO = 196;
    private static final int TABS_WIDTH_THREE = 284;
    private static final int TABS_WIDTH_FOUR = 320;
    private static final int TABS_WIDTH_FIVE = 388;
    private static final int TABS_WIDTH_SIX = 456;

    private static int indexToPosition(int index) {
        return index;
    }



    private static final int ANIMATOR_ID_TABS_VISIBLE = 0;
    private final BoolAnimator animatorTabsVisible = new BoolAnimator(ANIMATOR_ID_TABS_VISIBLE,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 380, true);


    private IUpdateLayout updateLayout;
    private boolean dropCallsFragmentAfterPageScroll;

    private UpdateLayoutWrapper updateLayoutWrapper;
    private MainTabsLayout tabsView;
    private BlurredBackgroundDrawable tabsViewBackground;
    private View fadeView;
    private ItemOptions readAllMessagesOptions;
    private ItemOptions accountSwitchOptions;
    private final Runnable retentionPrompt = () -> {
        if (!isFinished && !isPaused() && getParentActivity() != null) {
            org.telegram.ui.Components.DownloadRetentionPrompt.maybeShow(this);
        }
    };

    public MainTabsActivity() {
        super();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            iBlur3SourceTabGlass = new BlurredBackgroundSourceRenderNode(null);
            iBlur3SourceTabGlass.setupRenderer(new RenderNodeWithHash.Renderer() {
                @Override
                public void renderNodeCalculateHash(IBlur3Hash hash) {
                    hash.add(getThemedColor(Theme.key_windowBackgroundWhite));
                    hash.add(SharedConfig.chatBlurEnabled());

                    for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
                        final FragmentState state = fragmentsArr.valueAt(a);
                        final BaseFragment fragment = state.fragment;
                        if (fragment.fragmentView == null) {
                            continue;
                        }
                        if (!ViewPositionWatcher.computeRectInParent(fragment.fragmentView, contentView, fragmentPosition)) {
                            continue;
                        }
                        if (fragmentPosition.right <= 0 || fragmentPosition.left >= fragmentView.getMeasuredWidth()) {
                            continue;
                        }

                        if (fragment instanceof TabFragmentDelegate) {
                            TabFragmentDelegate delegate = (TabFragmentDelegate) fragment;
                            BlurredBackgroundSourceRenderNode source = delegate.getGlassSource();
                            if (source != null) {
                                hash.addF(fragmentPosition.left);
                                hash.addF(fragmentPosition.top);
                                hash.add(fragment.getClassGuid());
                            }
                        }
                    }
                }

                @Override
                public void renderNodeUpdateDisplayList(Canvas canvas) {
                    final int width = fragmentView.getMeasuredWidth();
                    final int height = fragmentView.getMeasuredHeight();

                    canvas.drawColor(getThemedColor(Theme.key_windowBackgroundWhite));

                    for (int a = 0, N = fragmentsArr.size(); a < N; a++) {
                        final FragmentState state = fragmentsArr.valueAt(a);
                        final BaseFragment fragment = state.fragment;
                        if (fragment.fragmentView == null) {
                            continue;
                        }
                        if (!ViewPositionWatcher.computeRectInParent(fragment.fragmentView, contentView, fragmentPosition)) {
                            continue;
                        }
                        if (fragmentPosition.right <= 0 || fragmentPosition.left >= fragmentView.getMeasuredWidth()) {
                            continue;
                        }

                        if (fragment instanceof TabFragmentDelegate) {
                            TabFragmentDelegate delegate = (TabFragmentDelegate) fragment;
                            BlurredBackgroundSourceRenderNode source = delegate.getGlassSource();
                            if (source != null) {
                                canvas.save();
                                canvas.translate(fragmentPosition.left, fragmentPosition.top);
                                source.draw(canvas, 0, 0, width, height);
                                canvas.restore();
                            }
                        }
                    }
                }
            });
        } else {
            iBlur3SourceTabGlass = null;
        }

        iBlur3SourceColor = new BlurredBackgroundSourceColor();
    }

    @Override
    protected FrameLayout createContentView(Context context) {
        return new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                checkUi_tabsPosition();
                checkUi_fadeView();
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                blur3_invalidateBlur();
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        blur3_updateColors();
        checkContactsTabBadge();
        restoreTabOrder();
        checkUnreadDialogsTabVisibility(true);
        checkUnreadCount(true);

        Bulletin.Delegate delegate = new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return navigationBarHeight + dp(DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN);
            }
        };

        Bulletin.addDelegate(this, delegate);
        Bulletin.addDelegate(contentView, delegate);

        showAccountChangeHint();
        AndroidUtilities.cancelRunOnUIThread(retentionPrompt);
        AndroidUtilities.runOnUIThread(retentionPrompt, 800);
    }

    private void checkContactsTabBadge() {
    }

    @Override
    public void onPause() {
        AndroidUtilities.cancelRunOnUIThread(retentionPrompt);
        super.onPause();
        Bulletin.removeDelegate(this);
        Bulletin.removeDelegate(contentView);
        if (accountSwitchHint != null) {
            accountSwitchHint.hide();
        }
        if (readAllMessagesOptions != null) {
            readAllMessagesOptions.dismiss();
            readAllMessagesOptions = null;
        }
    }

    @Override
    public View createView(Context context) {
        super.createView(context);

        tabsView = new MainTabsLayout(context);
        tabsView.setClipChildren(false);
        tabsView.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4), dp(DialogsActivity.MAIN_TABS_MARGIN + 4));

        tabs = new GlassTabView[TABS_COUNT];
        tabs[INDEX_CONTACTS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CONTACTS, R.string.ContactsTabTitle);
        tabs[INDEX_CHANNEL_FEED] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.REPLIES, R.string.ChannelFeedTitle);
        tabs[INDEX_UNREAD] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CHECKLIST, R.string.MainTabsUnread);
        tabs[INDEX_CHATS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CHATS, R.string.MainTabsChats);
        tabs[INDEX_SETTINGS] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.SETTINGS, R.string.Settings);
        tabs[INDEX_PROFILE] = GlassTabView.createAvatar(context, resourceProvider, currentAccount, R.string.MainTabsProfile);
        tabs[INDEX_MESSAGE_CENTER] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.CHECKLIST, R.string.MessageManagementTab);
        tabs[INDEX_UNIFIED_INBOX] = GlassTabView.createMainTab(context, resourceProvider, GlassTabView.TabAnimation.REPLIES, R.string.MangoUnifiedTab);

        for (int index = 0; index < tabs.length; index++) {
            final GlassTabView view = tabs[index];

            final int position = indexToPosition(index);
            tabs[index].setOnClickListener(v -> {
                if (viewPager.isManualScrolling() || viewPager.isTouch()) {
                    return;
                }
                if (position == POSITION_UNIFIED_INBOX && !CrossAccountInboxActivity.isEnabled(currentAccount)) {
                    return;
                }

                if (viewPager.getCurrentPosition() == position) {
                    final BaseFragment fragment = getCurrentVisibleFragment();
                    if (fragment instanceof MainTabsActivity.TabFragmentDelegate) {
                        ((MainTabsActivity.TabFragmentDelegate) fragment).onParentScrollToTop();
                    }
                    return;
                }

                selectTab(position, true);
                viewPager.scrollToPosition(position);
            });
            tabs[index].setOnLongClickListener(v -> {
                if (viewPager.isManualScrolling() || viewPager.isTouch()) return false;
                return tabsView.startReordering(v);
            });

            tabsView.addView(tabs[index]);
            tabsView.setViewVisible(view, true, false);
        }

        tabsView.setReorderListener(new MainTabsLayout.ReorderListener() {
            @Override
            public void onOrderChanged(int[] order) {
                MangoSettings.setString(currentAccount, MainTabsOrder.SETTING_KEY, MainTabsOrder.encode(order));
            }

            @Override
            public void onLongPressWithoutMove(View tab) {
                if (tab == tabs[INDEX_UNREAD] || tab == tabs[INDEX_CHATS]) {
                    showReadAllMessagesOptions(tab);
                } else if (tab == tabs[INDEX_PROFILE]) {
                    showAccountSwitchOptions(tab);
                } else if (tab == tabs[INDEX_SETTINGS]) {
                    ExtensionFeaturesActivity extension = new ExtensionFeaturesActivity();
                    extension.setCurrentAccount(currentAccount);
                    presentFragment(extension);
                }
            }
        });
        // A very subtle tint: the bar itself is already glass, the lens only has to
        // separate the selected tab from the rest of it.
        tabsView.setLensColors(
                Theme.multAlpha(getThemedColor(Theme.key_glass_defaultIcon), 24 / 255f),
                Theme.multAlpha(getThemedColor(Theme.key_glass_defaultIcon), 32 / 255f));
        restoreTabOrder();
        selectTab(viewPager.getCurrentPosition(), false);
        checkUnreadDialogsTabVisibility(false);

        iBlur3SourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));


        ViewPositionWatcher viewPositionWatcher = new ViewPositionWatcher(contentView);


        BlurredBackgroundDrawableViewFactory iBlur3FactoryGlass = new BlurredBackgroundDrawableViewFactory(iBlur3SourceTabGlass != null ? iBlur3SourceTabGlass : iBlur3SourceColor);
        iBlur3FactoryGlass.setSourceRootView(viewPositionWatcher, contentView);
        iBlur3FactoryGlass.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));

        tabsViewBackground = iBlur3FactoryGlass.create(tabsView, BlurredBackgroundProviderImpl.mainTabs(resourceProvider));
        tabsViewBackground.setRadius(dp(DialogsActivity.MAIN_TABS_HEIGHT / 2f));
        tabsViewBackground.setPadding(dp(DialogsActivity.MAIN_TABS_MARGIN - 0.334f));
        tabsView.setBackground(tabsViewBackground);

        BlurredBackgroundDrawableViewFactory iBlur3FactoryFade = new BlurredBackgroundDrawableViewFactory(iBlur3SourceColor);
        iBlur3FactoryFade.setSourceRootView(viewPositionWatcher, contentView);

        fadeView = new View(context);
        BlurredBackgroundWithFadeDrawable fadeDrawable = new BlurredBackgroundWithFadeDrawable(iBlur3FactoryFade.create(fadeView, null));
        fadeDrawable.setFadeHeight(dp(60), true);
        fadeView.setBackground(fadeDrawable);

        contentView.addView(fadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.BOTTOM));
        contentView.addView(tabsView, LayoutHelper.createFrame(TABS_WIDTH_THREE + DialogsActivity.MAIN_TABS_MARGIN * 2, DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        checkUnreadDialogsTabVisibility(false);

        updateLayoutWrapper = new UpdateLayoutWrapper(context);
        contentView.addView(updateLayoutWrapper, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        updateLayout = ApplicationLoader.applicationLoaderInstance.takeUpdateLayout(getParentActivity(), updateLayoutWrapper);
        if (updateLayout != null) {
            updateLayout.updateAppUpdateViews(currentAccount, false);
        }

        //AndroidUtilities.cancelRunOnUIThread(justForTestR);
        //AndroidUtilities.runOnUIThread(justForTestR, 2000);

        checkUnreadCount(false);
        return contentView;
    }

    private void restoreTabOrder() {
        if (tabsView != null) {
            tabsView.setTabOrder(MainTabsOrder.decode(
                    MangoSettings.getString(currentAccount, MainTabsOrder.SETTING_KEY, ""), TABS_COUNT));
        }
    }

    private void checkUnreadCount(boolean animated) {
        if (tabsView == null) {
            return;
        }

        final int unreadCount = MessagesStorage.getInstance(currentAccount).getMainUnreadCount();
        if (unreadCount > 0) {
            final String unreadCountFmt = LocaleController.formatNumber(unreadCount, ',');
            tabs[INDEX_CHATS].setCounter(unreadCountFmt, false, animated);
        } else {
            tabs[INDEX_CHATS].setCounter(null, false, animated);
        }

        if (tabs[INDEX_UNREAD] != null && isUnreadDialogsTabEnabled()) {
            final int unreadUnmutedCount = DialogsActivity.getUnreadUnmutedDialogs(getMessagesController(), 0).size();
            if (unreadUnmutedCount > 0) {
                tabs[INDEX_UNREAD].setCounter(LocaleController.formatNumber(unreadUnmutedCount, ','), false, animated);
            } else {
                tabs[INDEX_UNREAD].setCounter(null, false, animated);
            }
        } else if (tabs[INDEX_UNREAD] != null) {
            tabs[INDEX_UNREAD].setCounter(null, false, animated);
        }
    }

    private boolean isUnreadDialogsTabEnabled() {
        return getMessagesController().isUnreadDialogsTabEnabled();
    }

    private boolean isContactsTabEnabled() {
        return getUserConfig().showContactsTab;
    }

    private boolean isChannelFeedTabEnabled() {
        return SharedConfig.isChannelFeedEnabled();
    }

    private int getTabsWidthDp(boolean contactsVisible, boolean unreadVisible, boolean channelFeedVisible) {
        int visibleCount = 3 + (contactsVisible ? 1 : 0) + (unreadVisible ? 1 : 0) + (channelFeedVisible ? 1 : 0) + (MessageActionSettings.isCenterEnabled(currentAccount) ? 1 : 0) + (CrossAccountInboxActivity.isEnabled(currentAccount) ? 1 : 0);
        if (visibleCount <= 2) {
            return TABS_WIDTH_TWO;
        } else if (visibleCount == 3) {
            return TABS_WIDTH_THREE;
        } else if (visibleCount == 4) {
            return TABS_WIDTH_FOUR;
        } else if (visibleCount == 5) {
            return TABS_WIDTH_FIVE;
        }
        return TABS_WIDTH_SIX;
    }

    private void checkUnreadDialogsTabVisibility(boolean animated) {
        if (tabsView == null || tabs == null || tabs[INDEX_CONTACTS] == null || tabs[INDEX_UNREAD] == null || tabs[INDEX_CHANNEL_FEED] == null) {
            return;
        }
        final boolean contactsVisible = isContactsTabEnabled();
        final boolean unreadVisible = isUnreadDialogsTabEnabled();
        final boolean channelFeedVisible = isChannelFeedTabEnabled();
        tabsView.setViewVisible(tabs[INDEX_CONTACTS], contactsVisible, animated);
        tabsView.setViewVisible(tabs[INDEX_CHANNEL_FEED], channelFeedVisible, animated);
        tabsView.setViewVisible(tabs[INDEX_UNREAD], unreadVisible, animated);
        final boolean messageCenterVisible = MessageActionSettings.isCenterEnabled(currentAccount);
        tabsView.setViewVisible(tabs[INDEX_MESSAGE_CENTER], messageCenterVisible, animated);
        final boolean unifiedInboxVisible = CrossAccountInboxActivity.isEnabled(currentAccount);
        tabsView.setViewVisible(tabs[INDEX_UNIFIED_INBOX], unifiedInboxVisible, animated);
        ViewGroup.LayoutParams lp = tabsView.getLayoutParams();
        if (lp != null) {
            int availableWidth = contentView != null && contentView.getMeasuredWidth() > 0 ? contentView.getMeasuredWidth() : AndroidUtilities.displaySize.x;
            int targetWidth = Math.min(availableWidth, dp(getTabsWidthDp(contactsVisible, unreadVisible, channelFeedVisible) + DialogsActivity.MAIN_TABS_MARGIN * 2));
            if (lp.width != targetWidth) {
                lp.width = targetWidth;
                tabsView.setLayoutParams(lp);
            }
        }
        int currentPosition = viewPager != null ? viewPager.getCurrentPosition() : POSITION_CHATS;
        if ((!contactsVisible && currentPosition == POSITION_CONTACTS) || (!unreadVisible && currentPosition == POSITION_UNREAD) || (!channelFeedVisible && currentPosition == POSITION_CHANNEL_FEED) || (!messageCenterVisible && currentPosition == POSITION_MESSAGE_CENTER) || (!unifiedInboxVisible && currentPosition == POSITION_UNIFIED_INBOX)) {
            selectTab(POSITION_CHATS, animated);
            viewPager.scrollToPosition(POSITION_CHATS);
        } else {
            selectTab(currentPosition, animated);
        }
        if (!unifiedInboxVisible && currentPosition != POSITION_UNIFIED_INBOX
                && fragmentsArr.get(POSITION_UNIFIED_INBOX) != null) {
            dropFragmentAtPosition(POSITION_UNIFIED_INBOX);
        }
        checkUnreadCount(animated);
    }

    private void showReadAllMessagesOptions(View anchor) {
        if (anchor == null || getContext() == null) {
            return;
        }
        if (readAllMessagesOptions != null && readAllMessagesOptions.isShown()) {
            readAllMessagesOptions.dismiss();
        }
        readAllMessagesOptions = ItemOptions.makeOptions(this, anchor)
                .add(R.drawable.msg_markread, getString(R.string.UnreadDialogsMarkAllAsRead), () -> {
                    ArrayList<TLRPC.Dialog> dialogs = DialogsActivity.getUnreadUnmutedDialogs(getMessagesController(), 0);
                    DialogsActivity.markDialogsAsRead(currentAccount, getMessagesController(), dialogs);
                    checkUnreadCount(true);
                })
                .addIf(anchor == tabs[INDEX_CHATS] && getMessagesController().isFrozen() && !getFrozenUserDialogs().isEmpty(), R.drawable.msg_delete, getString(R.string.FrozenAccountClearChatsTitle), this::showFrozenAccountClearChatsConfirm)
                .setDimAlpha(0x08)
                .setGravity(Gravity.CENTER)
                .translate(0, -dp(4))
                .show();
    }

    private ArrayList<TLRPC.Dialog> getFrozenUserDialogs() {
        ArrayList<TLRPC.Dialog> result = new ArrayList<>();
        if (!getMessagesController().isFrozen()) {
            return result;
        }
        ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getDialogs(0);
        for (int i = 0, size = dialogs.size(); i < size; i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog == null || dialog.id <= 0 || DialogObject.isEncryptedDialog(dialog.id)) {
                continue;
            }
            TLRPC.User user = getMessagesController().getUser(dialog.id);
            if (user == null || UserObject.isUserSelf(user)) {
                continue;
            }
            result.add(dialog);
        }
        return result;
    }

    private void showFrozenAccountClearChatsConfirm() {
        if (getContext() == null) {
            return;
        }
        ArrayList<TLRPC.Dialog> dialogs = getFrozenUserDialogs();
        if (dialogs.isEmpty()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourceProvider);
        builder.setTitle(getString(R.string.FrozenAccountClearChatsTitle));
        builder.setMessage(LocaleController.formatString(R.string.FrozenAccountClearChatsInfo, dialogs.size()));
        builder.setPositiveButton(getString(R.string.Clear), (dialog, which) -> {
            for (int i = 0, size = dialogs.size(); i < size; i++) {
                getMessagesController().deleteDialog(dialogs.get(i).id, 0, false);
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
            checkUnreadCount(true);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    public void openAccountSelector(View button) {
        showAccountSwitchOptions(button);
    }

    private void showAccountSwitchOptions(View anchor) {
        if (anchor == null || getContext() == null) {
            return;
        }
        ArrayList<Integer> accounts = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                accounts.add(a);
            }
        }
        if (accounts.size() <= 1) {
            return;
        }
        if (accountSwitchOptions != null && accountSwitchOptions.isShown()) {
            accountSwitchOptions.dismiss();
        }
        ItemOptions options = ItemOptions.makeOptions(this, anchor)
                .setDimAlpha(0x08)
                .setGravity(Gravity.CENTER)
                .translate(0, -dp(4));
        for (int i = 0; i < accounts.size(); i++) {
            final int account = accounts.get(i);
            options.addAccount(account, account == currentAccount, () -> {
                if (LaunchActivity.instance != null && account != currentAccount) {
                    LaunchActivity.instance.switchToAccount(account, true);
                }
            });
        }
        accountSwitchOptions = options.show();
    }

    public LinearLayout accountView(int account, boolean selected) {
        final LinearLayout btn = new LinearLayout(getContext());
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_listSelector), 0, 0));

        final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();

        final AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setInfo(user);

        final FrameLayout avatarContainer = new FrameLayout(getContext()) {
            private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                if (selected) {
                    selectedPaint.setStyle(Paint.Style.STROKE);
                    selectedPaint.setStrokeWidth(dp(1.33f));
                    selectedPaint.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
                    canvas.drawCircle(getWidth() / 2.0f, getHeight() / 2.0f, dp(16), selectedPaint);
                }
                super.dispatchDraw(canvas);
            }
        };
        btn.addView(avatarContainer, LayoutHelper.createLinear(34, 34, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        final BackupImageView avatarView = new BackupImageView(getContext());
        if (selected) {
            avatarView.setScaleX(0.833f);
            avatarView.setScaleY(0.833f);
        }
        avatarView.setRoundRadius(dp(16));
        avatarView.getImageReceiver().setCurrentAccount(account);
        avatarView.setForUserOrChat(user, avatarDrawable);
        avatarContainer.addView(avatarView, LayoutHelper.createLinear(32, 32, Gravity.CENTER, 1, 1, 1, 1));

        final TextView textView = new TextView(getContext());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        textView.setText(UserObject.getUserName(user));
        textView.setMaxLines(2);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        btn.addView(textView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 13, 0, 14, 0));

        return btn;
    }

    @Override
    protected void onViewPagerScrollEnd() {
        if (tabsView != null) {
            selectTab(viewPager.getCurrentPosition(), true);
            setGestureSelectedOverride(0, false);
        }
        blur3_invalidateBlur();

        // A disabled summary must not keep observers, rows, or account caches
        // attached through a retained pager fragment. Destroy it only after
        // the page transition has left it.
        if (viewPager != null && viewPager.getCurrentPosition() != POSITION_UNIFIED_INBOX
                && !CrossAccountInboxActivity.isEnabled(currentAccount)
                && fragmentsArr.get(POSITION_UNIFIED_INBOX) != null) {
            dropFragmentAtPosition(POSITION_UNIFIED_INBOX);
        }

        if (viewPager != null) {
            final int currentPosition = viewPager.getCurrentPosition();
            if (currentPosition != POSITION_SETTINGS && dropCallsFragmentAfterPageScroll) {
                dropFragmentAtPosition(POSITION_SETTINGS);
                dropCallsFragmentAfterPageScroll = false;
            }
        }

    }

    @Override
    protected void onViewPagerTabAnimationUpdate(boolean manual) {
        final boolean isDragByGesture = !manual;

        if (tabsView != null) {
            final float position = viewPager.getPositionAnimated();
            setGestureSelectedOverride(position, isDragByGesture);
            if (isDragByGesture) {
                selectTab(Math.round(position), true);
            }
        }

        checkUi_fadeView();
        blur3_invalidateBlur();
    }


    @Override
    protected int getFragmentsCount() {
        return TABS_COUNT;
    }

    @Override
    protected int getStartPosition() {
        return POSITION_CHATS;
    }

    private DialogsActivity dialogsActivity;

    @Override
    public boolean onBackPressed(boolean invoked) {
        final boolean result = super.onBackPressed(invoked);
        if (result) {
            final int startPosition = getStartPosition();
            if (viewPager.getCurrentPosition() != startPosition) {
                if (invoked) {
                    viewPager.scrollToPosition(startPosition);
                }
                return false;
            }
        }
        return result;
    }

    public DialogsActivity prepareDialogsActivity(Bundle bundle) {
        if (bundle == null) {
            bundle = new Bundle();
        }

        bundle.putBoolean("hasMainTabs", true);
        bundle.putBoolean("allowSwitchAccount", true);
        dialogsActivity = new DialogsActivity(bundle);
        dialogsActivity.setMainTabsActivityController(new MainTabsActivityControllerImpl());
        putFragmentAtPosition(POSITION_CHATS, dialogsActivity);
        return dialogsActivity;
    }

    @Override
    protected BaseFragment createBaseFragmentAt(int position) {
        if (position == POSITION_CONTACTS) {
            Bundle args = new Bundle();
            args.putBoolean("hasMainTabs", true);
            args.putBoolean("destroyAfterSelect", false);
            return new ContactsActivity(args);
        } else if (position == POSITION_CHANNEL_FEED) {
            return new ChannelFeedActivity();
        } else if (position == POSITION_MESSAGE_CENTER) {
            MessageManagementActivity center = new MessageManagementActivity(true);
            center.setCurrentAccount(currentAccount);
            return center;
        } else if (position == POSITION_UNIFIED_INBOX) {
            CrossAccountInboxActivity inbox = new CrossAccountInboxActivity(true);
            inbox.setCurrentAccount(currentAccount);
            return inbox;
        } else if (position == POSITION_SETTINGS) {
            Bundle args = new Bundle();
            args.putBoolean("hasMainTabs", true);
            return new SettingsActivity(args);
        } else if (position == POSITION_PROFILE) {
            long selfId = getUserConfig().getClientUserId();
            TLRPC.User currentUser = getUserConfig().getCurrentUser();
            if (getMessagesController().getUser(selfId) == null && currentUser != null) {
                getMessagesController().putUser(currentUser, false);
            }
            Bundle args = new Bundle();
            args.putLong("user_id", selfId);
            args.putBoolean("my_profile", true);
            args.putBoolean("hasMainTabs", true);
            return new ProfileActivity(args);
        } else if (position == POSITION_UNREAD) {
            Bundle args = new Bundle();
            args.putBoolean("hasMainTabs", true);
            args.putBoolean("allowSwitchAccount", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_UNREAD_UNMUTED);
            DialogsActivity unreadDialogsActivity = new DialogsActivity(args);
            unreadDialogsActivity.setMainTabsActivityController(new MainTabsActivityControllerImpl());
            return unreadDialogsActivity;
        } else if (position == POSITION_CHATS) {
            Bundle args = new Bundle();
            args.putBoolean("hasMainTabs", true);
            args.putBoolean("allowSwitchAccount", true);
            dialogsActivity = new DialogsActivity(args);
            dialogsActivity.setMainTabsActivityController(new MainTabsActivityControllerImpl());
            return dialogsActivity;
        }
        return null;
    }

    public DialogsActivity getDialogsActivity() {
        return dialogsActivity;
    }

    /* */

    public GlassTabView[] tabs;

    public void selectTab(int position, boolean animated) {
        for (int a = 0; a < tabs.length; a++) {
            GlassTabView tab = tabs[a];
            tab.setSelected(indexToPosition(a) == position, animated);
        }
        updateTabsLens(position);
    }

    public void setGestureSelectedOverride(float animatedPosition, boolean allow) {
        for (int index = 0; index < tabs.length; index++) {
            final int position = indexToPosition(index);
            final float visibility = Math.max(0, 1f - Math.abs(position - animatedPosition));
            tabs[index].setGestureSelectedOverride(visibility, allow);
        }
        updateTabsLens(animatedPosition);
        tabsView.invalidate();
    }

    /** Drives the floating glass lens from the continuous page position. */
    private void updateTabsLens(float animatedPosition) {
        if (tabsView == null || tabs == null) {
            return;
        }

        final int lo = (int) Math.floor(animatedPosition);
        final float fraction = animatedPosition - lo;
        int fromIndex = -1;
        int toIndex = -1;
        for (int a = 0; a < tabs.length; a++) {
            final int position = indexToPosition(a);
            if (position == lo) fromIndex = a;
            if (position == lo + 1) toIndex = a;
        }

        if (fromIndex < 0 && toIndex < 0) {
            tabsView.setLensTarget(-1, -1, 0);
            return;
        }

        if (fromIndex < 0) fromIndex = toIndex;
        if (toIndex < 0) toIndex = fromIndex;
        tabsView.setLensTarget(fromIndex, toIndex, fromIndex == toIndex ? 0 : fraction);
    }


    /* * */

    public interface TabFragmentDelegate {
        default boolean canParentTabsSlide(MotionEvent ev, boolean forward) {
            return false;
        }

        default void onParentScrollToTop() {

        }

        default BlurredBackgroundSourceRenderNode getGlassSource() {
            return null;
        }
    }

    @Override
    protected boolean canScrollForward(MotionEvent ev) {
        return canScrollInternal(ev, true);
    }

    @Override
    protected boolean canScrollBackward(MotionEvent ev) {
        return canScrollInternal(ev, false);
    }

    private boolean canScrollInternal(MotionEvent ev, boolean forward) {
        if (forward && viewPager != null && viewPager.getCurrentPosition() == POSITION_MESSAGE_CENTER && !CrossAccountInboxActivity.isEnabled(currentAccount)) {
            return false;
        }
        if (!forward && viewPager != null && viewPager.getCurrentPosition() == POSITION_UNIFIED_INBOX && !MessageActionSettings.isCenterEnabled(currentAccount)) {
            return false;
        }
        if (forward && viewPager != null && viewPager.getCurrentPosition() == POSITION_PROFILE && !MessageActionSettings.isCenterEnabled(currentAccount)) {
            return false;
        }
        if (!forward && viewPager != null && viewPager.getCurrentPosition() == POSITION_CHATS && !isUnreadDialogsTabEnabled()) {
            return false;
        }
        if (!forward && viewPager != null && viewPager.getCurrentPosition() == POSITION_UNREAD) {
            return isChannelFeedTabEnabled();
        }
        if (!forward && viewPager != null && viewPager.getCurrentPosition() == POSITION_CHANNEL_FEED) {
            return isContactsTabEnabled();
        }
        if (forward && viewPager != null && viewPager.getCurrentPosition() == POSITION_CONTACTS) {
            return isChannelFeedTabEnabled();
        }
        if (forward && viewPager != null && viewPager.getCurrentPosition() == POSITION_CHANNEL_FEED) {
            return isUnreadDialogsTabEnabled();
        }
        if (forward && viewPager != null && viewPager.getCurrentPosition() == POSITION_UNREAD && isUnreadDialogsTabEnabled()) {
            return true;
        }
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment instanceof TabFragmentDelegate) {
            final TabFragmentDelegate delegate = (TabFragmentDelegate) fragment;
            return delegate.canParentTabsSlide(ev, forward);

        }

        return false;
    }


    /* * */

    private int navigationBarHeight;

    @NonNull
    @Override
    protected WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        final boolean isUpdateLayoutVisible = updateLayoutWrapper.isUpdateLayoutVisible();
        final int updateLayoutHeight = isUpdateLayoutVisible ? dp(UpdateLayoutWrapper.HEIGHT) : 0;
        updateLayoutWrapper.setPadding(0, 0, 0, navigationBarHeight);

        ViewGroup.MarginLayoutParams lp;
        {
            final int height = navigationBarHeight + updateLayoutHeight + dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS);
            lp = (ViewGroup.MarginLayoutParams) fadeView.getLayoutParams();
            if (lp.height != height) {
                lp.height = height;
                fadeView.setLayoutParams(lp);
            }
        }
        {
            final int bottomMargin = isUpdateLayoutVisible ? (navigationBarHeight + updateLayoutHeight) : 0;
            lp = (ViewGroup.MarginLayoutParams) viewPager.getLayoutParams();
            if (lp.bottomMargin != bottomMargin) {
                lp.bottomMargin = bottomMargin;
                viewPager.setLayoutParams(lp);
            }
        }

        final WindowInsetsCompat consumed = isUpdateLayoutVisible ?
            insets.inset(0, 0, 0, navigationBarHeight) : insets;

        checkUi_tabsPosition();
        checkUi_fadeView();

        return super.onApplyWindowInsets(v, consumed);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.notificationsCountUpdated || id == NotificationCenter.updateInterfaces || id == NotificationCenter.contactsTabVisibleToggled) {
            if (id == NotificationCenter.updateInterfaces || id == NotificationCenter.contactsTabVisibleToggled) {
                checkUnreadDialogsTabVisibility(fragmentView != null && fragmentView.isAttachedToWindow());
            }
            checkUnreadCount(fragmentView != null && fragmentView.isAttachedToWindow());
        } else if (id == NotificationCenter.mainUserInfoChanged) {
            if (tabs != null && tabs[INDEX_PROFILE] != null) {
                tabs[INDEX_PROFILE].updateUserAvatar(currentAccount);
            }
        } else if (id == NotificationCenter.appUpdateLoading) {
            if (updateLayout != null) {
                updateLayout.updateFileProgress(null);
                updateLayout.updateAppUpdateViews(currentAccount, true);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            String path = (String) args[0];
            if (SharedConfig.isAppUpdateAvailable()) {
                String name = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
                if (name.equals(path) && updateLayout != null) {
                    updateLayout.updateAppUpdateViews(currentAccount, true);
                }
            }
        } else if (id == NotificationCenter.fileLoadFailed) {
            String path = (String) args[0];
            if (SharedConfig.isAppUpdateAvailable()) {
                String name = FileLoader.getAttachFileName(SharedConfig.pendingAppUpdate.document);
                if (name.equals(path) && updateLayout != null) {
                    updateLayout.updateAppUpdateViews(currentAccount, true);
                }
            }
        } else if (id == NotificationCenter.fileLoadProgressChanged) {
            if (updateLayout != null) {
                updateLayout.updateFileProgress(args);
            }
        } else if (id == NotificationCenter.appUpdateAvailable) {
            if (updateLayout != null) {
                updateLayout.updateAppUpdateViews(currentAccount, LaunchActivity.getMainFragmentsStackSize() == 1);
            }
        } else if (id == NotificationCenter.needSetDayNightTheme) {
            clearAllHiddenFragments();
        } else if (id == NotificationCenter.contactsPermissionBadgeCheck) {
            checkContactsTabBadge();
        }
    }

    /* Just For Test */

    //private final Runnable justForTestR = this::justForTest;

    //private void justForTest() {
    //    getUserConfig().setShowCallsTab(!getUserConfig().showCallsTab);
    //    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.callTabsVisibleToggled);
    //    AndroidUtilities.cancelRunOnUIThread(justForTestR);
    //    AndroidUtilities.runOnUIThread(justForTestR, 3000);
    //}



    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.notificationsCountUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.mainUserInfoChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsPermissionBadgeCheck);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.contactsTabVisibleToggled);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.appUpdateAvailable);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.appUpdateLoading);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.needSetDayNightTheme);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(retentionPrompt);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoadFailed);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.notificationsCountUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.mainUserInfoChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsPermissionBadgeCheck);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.contactsTabVisibleToggled);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.appUpdateAvailable);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.appUpdateLoading);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.needSetDayNightTheme);

        super.onFragmentDestroy();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_TABS_VISIBLE) {
            checkUi_tabsPosition();
            checkUi_fadeView();
        }
    }

    private void checkUi_fadeView() {
        if (viewPager == null || fadeView == null) {
            return;
        }

        final float alpha = animatorTabsVisible.getFloatValue();
        fadeView.setAlpha(alpha);
        fadeView.setTranslationY(0);
        fadeView.setVisibility(alpha > 0 ? View.VISIBLE : View.GONE);
    }

    private void checkUi_tabsPosition() {
        final boolean isUpdateLayoutVisible = updateLayoutWrapper.isUpdateLayoutVisible();
        final int updateLayoutHeight = isUpdateLayoutVisible ? dp(UpdateLayoutWrapper.HEIGHT) : 0;
        final int normalY = -(navigationBarHeight + updateLayoutHeight);
        final int hiddenY = normalY + dp(40);

        final float factor = animatorTabsVisible.getFloatValue();
        final float scale = lerp(0.85f, 1f, factor);

        tabsView.setTranslationY(lerp(hiddenY, normalY, factor));
        tabsView.setScaleX(scale);
        tabsView.setScaleY(scale);
        tabsView.setClickable(factor > 1);
        tabsView.setEnabled(factor > 1);
        tabsView.setAlpha(factor);
        tabsView.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = super.getThemeDescriptions();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = this::blur3_updateColors;
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_dialogBackground));

        return themeDescriptions;
    }

    /* * */

    private class MainTabsActivityControllerImpl implements MainTabsActivityController {
        @Override
        public void setTabsVisible(boolean visible) {
            animatorTabsVisible.setValue(visible, true);
        }
    }


    /* Slide */

    @Override
    public boolean canBeginSlide() {
        final BaseFragment fragment = getCurrentVisibleFragment();
        return fragment != null && fragment.canBeginSlide();
    }

    @Override
    public void onBeginSlide() {
        super.onBeginSlide();
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onBeginSlide();
        }
    }

    @Override
    public void onSlideProgress(boolean isOpen, float progress) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.onSlideProgress(isOpen, progress);
        }
    }

    @Override
    public Animator getCustomSlideTransition(boolean topFragment, boolean backAnimation, float distanceToMove) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        return fragment != null ? fragment.getCustomSlideTransition(topFragment, backAnimation, distanceToMove) : null;
    }

    @Override
    public void prepareFragmentToSlide(boolean topFragment, boolean beginSlide) {
        final BaseFragment fragment = getCurrentVisibleFragment();
        if (fragment != null) {
            fragment.prepareFragmentToSlide(topFragment, beginSlide);
        }
    }


    private HintView2 accountSwitchHint;
    private boolean accountSwitchHintShown;

    private void showAccountChangeHint() {
        accountSwitchHintShown = true;
    }


    /* * */

    private final @NonNull BlurredBackgroundSourceColor iBlur3SourceColor;
    private final @Nullable BlurredBackgroundSourceRenderNode iBlur3SourceTabGlass;

    private final RectF fragmentPosition = new RectF();
    private void blur3_invalidateBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || iBlur3SourceTabGlass == null || fragmentView == null) {
            return;
        }

        final int width = fragmentView.getMeasuredWidth();
        final int height = fragmentView.getMeasuredHeight();

        iBlur3SourceTabGlass.setSize(width, height);
        iBlur3SourceTabGlass.updateDisplayListIfNeeded();
    }

    private void blur3_updateColors() {
        iBlur3SourceColor.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        if (tabsViewBackground != null) {
            tabsViewBackground.updateColors();
        }
        blur3_invalidateBlur();
        if (fadeView != null) {
            fadeView.invalidate();
        }
        if (tabsView != null) {
            tabsView.invalidate();
        }
        if (tabs != null) {
            for (GlassTabView tabView : tabs) {
                tabView.updateColorsLottie();
            }
        }
    }
}
