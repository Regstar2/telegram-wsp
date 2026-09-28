package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TgWsProxyController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

public class TgWsProxySettingsActivity extends BaseFragment {
    private static final int REQUEST_IMPORT_AWG = 4107;
    private static final int REQUEST_EXPORT_AWG = 4108;

    private TextCheckCell enabledCell;
    private TextSettingsCell statusCell;
    private TextSettingsCell routeCell;
    private TextSettingsCell cfSummaryCell;
    private TextView runtimeInfoCell;
    private TextView cfOperationCell;
    private TextView awgProfileInfoCell;
    private TextView awgOperationCell;
    private TextView workerSummaryCell;
    private EditText cfDomainsEdit;
    private EditText workerEdit;
    private TextView proxyWorkerTypeButton;
    private TextView awgWorkerTypeButton;

    private String proxyWorkerDraft = "";
    private String awgWorkerDraft = "";
    private boolean editingAwgWorkers;
    private String awgOperationStatus = "";

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(tr(context, "Встроенный прокси"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        addHeader(context, content, "TGWSPROXY");

        enabledCell = new TextCheckCell(context);
        enabledCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        enabledCell.setOnClickListener(v -> {
            boolean next = !enabledCell.isChecked();
            enabledCell.setChecked(next);
            TgWsProxyController.setEnabledAsync(context, next, (success, message) -> {
                if (!success) {
                    toast(context, message);
                }
                refreshUi(context);
            });
        });
        content.addView(enabledCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        statusCell = settingsCell(context);
        content.addView(statusCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        runtimeInfoCell = normalInfoText(context);
        content.addView(runtimeInfoCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        addShadow(context, content);
        addHeader(context, content, tr(context, "МАРШРУТИЗАЦИЯ"));

        routeCell = settingsCell(context);
        routeCell.setOnClickListener(v -> showRouteDialog(context));
        content.addView(routeCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        TextView wifi = normalInfoText(context);
        wifi.setText("Wi-Fi: CF Proxy → AWG* → Worker* → Direct");
        content.addView(wifi, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));
        TextView mobile = normalInfoText(context);
        mobile.setText(tr(context, "Мобильная сеть") + ": CF Proxy → AWG* → Worker*");
        content.addView(mobile, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        addHeader(context, content, "CLOUDFLARE PROXY");
        cfSummaryCell = settingsCell(context);
        content.addView(cfSummaryCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        cfDomainsEdit = multilineEditor(context, tr(context, "Свои домены — один hostname на строку"));
        content.addView(wrapEditor(context, cfDomainsEdit), LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 88
        ));

        TextSettingsCell updateDomains = actionCell(context, tr(context, "Обновить и проверить домены"));
        updateDomains.setOnClickListener(v -> {
            String error = TgWsProxyController.setManualCfDomains(context, cfDomainsEdit.getText().toString());
            if (!error.isEmpty()) {
                toast(context, error);
                return;
            }
            updateDomains.setEnabled(false);
            cfOperationCell.setText(tr(context, "Обновление списка и проверка доменов…"));
            toast(context, tr(context, "Проверка доменов запущена"));
            TgWsProxyController.updateAndCheckCfDomainsAsync(context, (success, message) -> {
                updateDomains.setEnabled(true);
                cfOperationCell.setText(success
                        ? tr(context, "Проверка завершена") + ": " + message
                        : tr(context, "Ошибка проверки") + ": " + message);
                toast(context, success ? tr(context, "Домены") + ": " + message : message);
                refreshUi(context);
            });
        });
        content.addView(updateDomains, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        cfOperationCell = normalInfoText(context);
        cfOperationCell.setText(tr(context, "Готово к обновлению и проверке"));
        content.addView(cfOperationCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        addShadow(context, content);
        addHeader(context, content, "WARP / AMNEZIAWG");

        awgProfileInfoCell = profileValueText(context);
        content.addView(awgProfileInfoCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        LinearLayout awgButtons = new LinearLayout(context);
        awgButtons.setOrientation(LinearLayout.VERTICAL);
        awgButtons.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4),
                AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        awgButtons.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        LinearLayout awgRow1 = new LinearLayout(context);
        awgRow1.setOrientation(LinearLayout.HORIZONTAL);
        TextView createAwgAction = actionButton(context, tr(context, "Создать"), false);
        createAwgAction.setOnClickListener(v -> showCreateAwgDialog(context));
        TextView importAwgAction = actionButton(context, tr(context, "Импорт .conf"), false);
        importAwgAction.setOnClickListener(v -> startAwgImport(context));
        addTwoButtons(awgRow1, createAwgAction, importAwgAction);
        awgButtons.addView(awgRow1, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 48
        ));

        LinearLayout awgRow2 = new LinearLayout(context);
        awgRow2.setOrientation(LinearLayout.HORIZONTAL);
        TextView exportAwgAction = actionButton(context, tr(context, "Экспорт .conf"), false);
        exportAwgAction.setOnClickListener(v -> startAwgExport(context));
        TextView deleteAwgAction = actionButton(context, tr(context, "Удалить"), true);
        deleteAwgAction.setOnClickListener(v -> confirmDeleteAwgProfile(context));
        addTwoButtons(awgRow2, exportAwgAction, deleteAwgAction);
        LinearLayout.LayoutParams awgRow2Params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48));
        awgRow2Params.topMargin = AndroidUtilities.dp(8);
        awgButtons.addView(awgRow2, awgRow2Params);

        content.addView(awgButtons, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        awgOperationCell = normalInfoText(context);
        awgOperationStatus = tr(context, "Готово");
        awgOperationCell.setText(tr(context, "Статус") + ": " + awgOperationStatus);
        content.addView(awgOperationCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        addShadow(context, content);
        addHeader(context, content, tr(context, "СВОИ WORKER"));

        workerSummaryCell = normalInfoText(context);
        content.addView(workerSummaryCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        LinearLayout workerTypeChooser = new LinearLayout(context);
        workerTypeChooser.setOrientation(LinearLayout.HORIZONTAL);
        workerTypeChooser.setPadding(
                AndroidUtilities.dp(12), AndroidUtilities.dp(8),
                AndroidUtilities.dp(12), AndroidUtilities.dp(8)
        );
        workerTypeChooser.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        proxyWorkerTypeButton = workerTypeButton(context, tr(context, "Для прокси"));
        proxyWorkerTypeButton.setOnClickListener(v -> switchWorkerEditor(false));
        awgWorkerTypeButton = workerTypeButton(context, tr(context, "Для Amnezia"));
        awgWorkerTypeButton.setOnClickListener(v -> switchWorkerEditor(true));

        LinearLayout.LayoutParams workerTypeParams =
                new LinearLayout.LayoutParams(0, AndroidUtilities.dp(44), 1f);
        workerTypeChooser.addView(proxyWorkerTypeButton, workerTypeParams);
        LinearLayout.LayoutParams awgTypeParams =
                new LinearLayout.LayoutParams(0, AndroidUtilities.dp(44), 1f);
        awgTypeParams.leftMargin = AndroidUtilities.dp(8);
        workerTypeChooser.addView(awgWorkerTypeButton, awgTypeParams);
        content.addView(workerTypeChooser, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));
        updateWorkerTypeSelector();

        TextView workerInputLabel = normalInfoText(context);
        workerInputLabel.setText(tr(context, "Адреса Worker"));
        content.addView(workerInputLabel, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        workerEdit = multilineEditor(context, tr(context, "Worker — один hostname на строку"));
        content.addView(wrapOutlinedEditor(context, workerEdit), LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 104, 0, 12, 4, 12, 8
        ));

        TextSettingsCell saveWorkers = actionCell(context, tr(context, "Сохранить и проверить Worker"));
        saveWorkers.setOnClickListener(v -> {
            saveCurrentWorkerDraft();
            String raw = editingAwgWorkers ? awgWorkerDraft : proxyWorkerDraft;
            saveWorkers.setEnabled(false);
            TgWsProxyController.saveAndCheckWorkersAsync(
                    context,
                    editingAwgWorkers,
                    raw,
                    (success, message) -> {
                        saveWorkers.setEnabled(true);
                        toast(context, success ? "Worker: " + message : message);
                        refreshUi(context);
                    }
            );
        });
        content.addView(saveWorkers, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        fragmentView = scrollView;
        refreshUi(context);
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getContext() != null) {
            refreshUi(getContext());
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null || getContext() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT_AWG) {
            setAwgOperationStatus(tr(getContext(), "Импорт и проверка профиля…"));
            TgWsProxyController.importAwgProfileAsync(
                    getContext(),
                    uri,
                    "Imported WARP",
                    (success, message) -> {
                        setAwgOperationStatus(success
                                ? tr(getContext(), "Профиль импортирован и проверен")
                                : tr(getContext(), "Ошибка импорта") + ": " + message);
                        toast(getContext(), success ? tr(getContext(), "Профиль импортирован") : message);
                        refreshUi(getContext());
                    }
            );
        } else if (requestCode == REQUEST_EXPORT_AWG) {
            setAwgOperationStatus(tr(getContext(), "Экспорт профиля…"));
            TgWsProxyController.exportAwgProfileAsync(
                    getContext(),
                    uri,
                    (success, message) -> {
                        setAwgOperationStatus(success
                                ? tr(getContext(), "Профиль экспортирован")
                                : tr(getContext(), "Ошибка экспорта") + ": " + message);
                        toast(getContext(), success ? tr(getContext(), "Профиль экспортирован") : message);
                    }
            );
        }
    }

    private void refreshUi(Context context) {
        TgWsProxyController.UiState state = TgWsProxyController.getUiState(context);
        if (enabledCell != null) {
            enabledCell.setTextAndCheck(
                    tr(context, "Использовать встроенный прокси"),
                    state.enabled,
                    true
            );
        }
        if (statusCell != null) {
            statusCell.setTextAndValue(tr(context, "Состояние"), localizeDynamic(context, state.status), true);
        }
        if (runtimeInfoCell != null) {
            if (!state.enabled) {
                runtimeInfoCell.setText(tr(context, "Встроенный прокси выключен"));
            } else {
                String backend = state.actualBackend;
                if (backend.isEmpty()) {
                    backend = TgWsProxyController.ROUTE_AUTO.equals(state.routeMode)
                            ? tr(context, "Автоматически")
                            : tr(context, "не выбран");
                }
                runtimeInfoCell.setText(state.lastError.isEmpty()
                        ? tr(context, "Активный backend") + ": " + backend
                        : tr(context, "Backend") + ": " + backend + "\n" + tr(context, "Ошибка runtime") + ": " + state.lastError);
            }
        }
        if (routeCell != null) {
            routeCell.setTextAndValue(
                    tr(context, "Режим маршрута"),
                    routeLabel(context, state.routeMode),
                    true
            );
        }
        if (cfSummaryCell != null) {
            cfSummaryCell.setTextAndValue(tr(context, "Домены"), localizeDynamic(context, state.cfSummary), true);
        }
        if (cfDomainsEdit != null && !cfDomainsEdit.hasFocus()) {
            cfDomainsEdit.setText(state.manualCfDomains);
        }
        if (awgProfileInfoCell != null) {
            awgProfileInfoCell.setText(tr(context, "Профиль") + ": " + localizeDynamic(context, state.awgSummary));
        }
        if (awgOperationCell != null) {
            awgOperationCell.setText(tr(context, "Статус") + ": " + awgOperationStatus);
        }
        proxyWorkerDraft = state.proxyWorkers;
        awgWorkerDraft = state.awgWorkers;
        if (workerSummaryCell != null) {
            workerSummaryCell.setText(localizeDynamic(context, state.workerSummary));
        }
        if (workerEdit != null && !workerEdit.hasFocus()) {
            workerEdit.setText(editingAwgWorkers ? awgWorkerDraft : proxyWorkerDraft);
        }
        updateWorkerTypeSelector();
    }

    private void showRouteDialog(Context context) {
        final String[] modes = {
                TgWsProxyController.ROUTE_AUTO,
                TgWsProxyController.ROUTE_CF_PROXY,
                TgWsProxyController.ROUTE_AWG,
                TgWsProxyController.ROUTE_WORKER,
                TgWsProxyController.ROUTE_DIRECT
        };
        final CharSequence[] labels = {
                tr(context, "Автоматически"),
                "Cloudflare Proxy",
                "WARP / AmneziaWG",
                "Cloudflare Worker",
                tr(context, "Напрямую")
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(tr(context, "Режим маршрута"));
        LinearLayout choices = new LinearLayout(context);
        choices.setOrientation(LinearLayout.VERTICAL);
        String selected = TgWsProxyController.getUiState(context).routeMode;
        for (int i = 0; i < modes.length; i++) {
            final int index = i;
            RadioColorCell cell = new RadioColorCell(context);
            cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            cell.setCheckColor(
                    Theme.getColor(Theme.key_radioBackground),
                    Theme.getColor(Theme.key_dialogRadioBackgroundChecked)
            );
            cell.setTextAndValue(labels[i].toString(), modes[i].equals(selected));
            cell.setBackground(Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL
            ));
            cell.setOnClickListener(v -> {
                builder.getDismissRunnable().run();
                TgWsProxyController.setRouteModeAsync(
                        context,
                        modes[index],
                        (success, message) -> {
                            if (!success) {
                                toast(context, message);
                            }
                            refreshUi(context);
                        }
                );
            });
            choices.addView(cell);
        }
        builder.setView(choices);
        builder.setNegativeButton(tr(context, "Отмена"), null);
        builder.show();
    }

    private void startAwgImport(Context context) {
        setAwgOperationStatus(tr(context, "Ожидание выбора .conf…"));
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_AWG);
    }

    private void startAwgExport(Context context) {
        setAwgOperationStatus(tr(context, "Ожидание места сохранения…"));
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "telegram-wsp-warp.conf");
        startActivityForResult(intent, REQUEST_EXPORT_AWG);
    }

    private void confirmDeleteAwgProfile(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(tr(context, "Удалить WARP / AmneziaWG профиль?"))
                .setMessage(tr(context, "Сохранённый конфиг будет удалён с устройства."))
                .setPositiveButton(tr(context, "Удалить"), (dialog, which) -> {
                    setAwgOperationStatus(tr(context, "Удаление профиля…"));
                    TgWsProxyController.deleteAwgProfileAsync(
                            context,
                            (success, message) -> {
                                setAwgOperationStatus(success
                                        ? tr(context, "Профиль удалён")
                                        : tr(context, "Ошибка удаления") + ": " + message);
                                toast(context, success ? tr(context, "Профиль удалён") : message);
                                refreshUi(context);
                            }
                    );
                })
                .setNegativeButton(tr(context, "Отмена"), null)
                .show();
    }

    private void showCreateAwgDialog(Context context) {
        EditText name = new EditText(context);
        name.setSingleLine(true);
        name.setText("WARP 1");
        name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        name.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        name.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(8),
                AndroidUtilities.dp(18), AndroidUtilities.dp(8));

        FrameLayout container = new FrameLayout(context);
        container.addView(name, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, 52, Gravity.CENTER, 12, 0, 12, 0
        ));

        new AlertDialog.Builder(context)
                .setTitle(tr(context, "Создать профиль"))
                .setMessage(tr(context, "Consumer WARP будет создан и проверен перед сохранением."))
                .setView(container)
                .setPositiveButton(tr(context, "Создать"), (dialog, which) -> {
                    setAwgOperationStatus(tr(context, "Создание ключей, регистрация и проверка профиля…"));
                    toast(context, tr(context, "Создание профиля запущено"));
                    TgWsProxyController.provisionAwgProfileAsync(
                            context,
                            name.getText().toString(),
                            (success, message) -> {
                                setAwgOperationStatus(success
                                        ? tr(context, "Профиль создан и проверен")
                                        : tr(context, "Ошибка создания") + ": " + message);
                                toast(context, success ? tr(context, "Профиль создан") : message);
                                refreshUi(context);
                            }
                    );
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void switchWorkerEditor(boolean awg) {
        if (workerEdit == null) {
            editingAwgWorkers = awg;
            return;
        }
        saveCurrentWorkerDraft();
        editingAwgWorkers = awg;
        workerEdit.setHint(awg
                ? tr(getContext(), "Amnezia bootstrap Worker — один hostname на строку")
                : tr(getContext(), "Proxy Worker — один hostname на строку"));
        workerEdit.setText(awg ? awgWorkerDraft : proxyWorkerDraft);
        updateWorkerTypeSelector();
    }

    private void saveCurrentWorkerDraft() {
        if (workerEdit == null) {
            return;
        }
        if (editingAwgWorkers) {
            awgWorkerDraft = workerEdit.getText().toString();
        } else {
            proxyWorkerDraft = workerEdit.getText().toString();
        }
    }

    private void setAwgOperationStatus(String status) {
        awgOperationStatus = status == null || status.trim().isEmpty()
                ? "Готово"
                : status.trim();
        if (awgOperationCell != null) {
            awgOperationCell.setText(tr(getContext(), "Статус") + ": " + awgOperationStatus);
        }
    }

    private void updateWorkerTypeSelector() {
        if (proxyWorkerTypeButton == null || awgWorkerTypeButton == null) {
            return;
        }
        applyWorkerTypeState(proxyWorkerTypeButton, !editingAwgWorkers);
        applyWorkerTypeState(awgWorkerTypeButton, editingAwgWorkers);
    }

    private static TextView workerTypeButton(Context context, String text) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(AndroidUtilities.bold());
        return button;
    }

    private static void applyWorkerTypeState(TextView button, boolean selected) {
        button.setTextColor(Theme.getColor(
                selected
                        ? Theme.key_featuredStickers_buttonText
                        : Theme.key_windowBackgroundWhiteBlueText
        ));
        button.setBackground(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(8),
                Theme.getColor(
                        selected
                                ? Theme.key_featuredStickers_addButton
                                : Theme.key_windowBackgroundGray
                )
        ));
    }

    private static TextView profileValueText(Context context) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        view.setPadding(
                AndroidUtilities.dp(17), AndroidUtilities.dp(12),
                AndroidUtilities.dp(17), AndroidUtilities.dp(12)
        );
        view.setMinHeight(AndroidUtilities.dp(48));
        view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return view;
    }

    private static void addHeader(Context context, LinearLayout content, String text) {
        HeaderCell header = new HeaderCell(context);
        header.setText(text);
        header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        content.addView(header, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));
    }

    private static void addShadow(Context context, LinearLayout content) {
        ShadowSectionCell shadow = new ShadowSectionCell(context);
        content.addView(shadow, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));
    }

    private static TextSettingsCell settingsCell(Context context) {
        TextSettingsCell cell = new TextSettingsCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return cell;
    }

    private static TextSettingsCell actionCell(Context context, String text) {
        TextSettingsCell cell = settingsCell(context);
        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        cell.setText(text, false);
        return cell;
    }

    private static EditText multilineEditor(Context context, String hint) {
        EditText edit = new EditText(context);
        edit.setGravity(Gravity.TOP | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        edit.setTextSize(15);
        edit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        edit.setHint(hint);
        edit.setMinLines(2);
        edit.setMaxLines(4);
        edit.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_VARIATION_URI
        );
        edit.setBackground(null);
        edit.setPadding(AndroidUtilities.dp(17), AndroidUtilities.dp(10),
                AndroidUtilities.dp(17), AndroidUtilities.dp(10));
        return edit;
    }

    private static FrameLayout wrapEditor(Context context, EditText edit) {
        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        frame.addView(edit, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT
        ));
        return frame;
    }

    private static FrameLayout wrapOutlinedEditor(Context context, EditText edit) {
        FrameLayout frame = new FrameLayout(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadius(AndroidUtilities.dp(8));
        background.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        frame.setBackground(background);
        frame.addView(edit, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT
        ));
        return frame;
    }

    private static TextView normalInfoText(Context context) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        view.setPadding(AndroidUtilities.dp(17), AndroidUtilities.dp(10),
                AndroidUtilities.dp(17), AndroidUtilities.dp(10));
        view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return view;
    }

    private static TextView actionButton(Context context, String text, boolean destructive) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        button.setTypeface(AndroidUtilities.bold());
        button.setGravity(Gravity.CENTER);
        if (destructive) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            button.setBackground(Theme.createRoundRectDrawable(
                    AndroidUtilities.dp(8), Theme.getColor(Theme.key_windowBackgroundGray)
            ));
        } else {
            button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            button.setBackground(Theme.AdaptiveRipple.filledRectByKey(
                    Theme.key_featuredStickers_addButton, 8
            ));
        }
        return button;
    }

    private static void addTwoButtons(LinearLayout row, View left, View right) {
        LinearLayout.LayoutParams leftParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        row.addView(left, leftParams);
        LinearLayout.LayoutParams rightParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        rightParams.leftMargin = AndroidUtilities.dp(8);
        row.addView(right, rightParams);
    }

    private static String routeLabel(Context context, String mode) {
        if (TgWsProxyController.ROUTE_CF_PROXY.equals(mode)) return "Cloudflare Proxy";
        if (TgWsProxyController.ROUTE_AWG.equals(mode)) return "WARP / AmneziaWG";
        if (TgWsProxyController.ROUTE_WORKER.equals(mode)) return "Cloudflare Worker";
        if (TgWsProxyController.ROUTE_DIRECT.equals(mode)) return tr(context, "Напрямую");
        return tr(context, "Автоматически");
    }

    private static String localizeDynamic(Context context, String value) {
        if (value == null || value.isEmpty()) return "";
        String result = value;
        String[][] replacements = {
                {"Работает", tr(context, "Работает")},
                {"Запускается", tr(context, "Запускается")},
                {"Выключен", tr(context, "Выключен")},
                {"Ограничено", tr(context, "Ограничено")},
                {"Остановлен", tr(context, "Остановлен")},
                {"Не настроен", tr(context, "Не настроен")},
                {"не проверен", tr(context, "не проверен")},
                {"работает", tr(context, "работает")},
                {"Встроенный fallback", tr(context, "Встроенный fallback")},
                {"не проверено", tr(context, "не проверено")},
                {"настроено", tr(context, "настроено")},
                {"только что", tr(context, "только что")},
                {"мин назад", tr(context, "мин назад")},
                {"ч назад", tr(context, "ч назад")},
                {"встроенных", tr(context, "встроенных")}
        };
        for (String[] replacement : replacements) {
            result = result.replace(replacement[0], replacement[1]);
        }
        return result;
    }

    private static String tr(Context context, String key) {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        String language = locale == null ? "en" : locale.getLanguage();
        String country = locale == null ? "" : locale.getCountry();
        if ("pt".equals(language) && "BR".equalsIgnoreCase(country)) {
            language = "pt-BR";
        }
        switch (language) {
            case "ru": return trRu(key);
            case "uk": return trUk(key);
            case "de": return trDe(key);
            case "es": return trEs(key);
            case "it": return trIt(key);
            case "nl": return trNl(key);
            case "pt-BR": return trPtBr(key);
            case "ar": return trAr(key);
            case "ko": return trKo(key);
            default: return trEn(key);
        }
    }

    private static String trEn(String key) {
        switch (key) {
            case "МАРШРУТИЗАЦИЯ": return "ROUTING";
            case "Мобильная сеть": return "Mobile network";
            case "СВОИ WORKER": return "CUSTOM WORKERS";
            case "Встроенный прокси": return "Built-in proxy";
            case "Использовать встроенный прокси": return "Use built-in proxy";
            case "Локальный MTProto-прокси для Telegram": return "Local MTProto proxy for Telegram";
            case "Состояние": return "State";
            case "Встроенный прокси выключен": return "Built-in proxy is disabled";
            case "Автоматически": return "Automatic";
            case "не выбран": return "not selected";
            case "Активный backend": return "Active backend";
            case "Backend": return "Backend";
            case "Ошибка runtime": return "Runtime error";
            case "Режим маршрута": return "Route mode";
            case "Напрямую": return "Direct";
            case "Домены": return "Domains";
            case "Свои домены — один hostname на строку": return "Custom domains — one hostname per line";
            case "Обновить и проверить домены": return "Update and check domains";
            case "Обновление списка и проверка доменов…": return "Updating and checking domains…";
            case "Проверка доменов запущена": return "Domain check started";
            case "Проверка завершена": return "Check completed";
            case "Ошибка проверки": return "Check failed";
            case "Готово к обновлению и проверке": return "Ready to update and check";
            case "Профиль": return "Profile";
            case "Создать": return "Create";
            case "Импорт .conf": return "Import .conf";
            case "Экспорт .conf": return "Export .conf";
            case "Удалить": return "Delete";
            case "Статус": return "Status";
            case "Готово": return "Ready";
            case "Для прокси": return "For proxy";
            case "Для Amnezia": return "For Amnezia";
            case "Адреса Worker": return "Worker addresses";
            case "Worker — один hostname на строку": return "Worker — one hostname per line";
            case "Сохранить и проверить Worker": return "Save and check Worker";
            case "Импорт и проверка профиля…": return "Importing and checking profile…";
            case "Профиль импортирован и проверен": return "Profile imported and checked";
            case "Ошибка импорта": return "Import error";
            case "Профиль импортирован": return "Profile imported";
            case "Экспорт профиля…": return "Exporting profile…";
            case "Профиль экспортирован": return "Profile exported";
            case "Ошибка экспорта": return "Export error";
            case "Ожидание выбора .conf…": return "Waiting for .conf selection…";
            case "Ожидание места сохранения…": return "Waiting for save location…";
            case "Удалить WARP / AmneziaWG профиль?": return "Delete WARP / AmneziaWG profile?";
            case "Сохранённый конфиг будет удалён с устройства.": return "The saved config will be removed from the device.";
            case "Удаление профиля…": return "Deleting profile…";
            case "Профиль удалён": return "Profile deleted";
            case "Ошибка удаления": return "Delete error";
            case "Отмена": return "Cancel";
            case "Создать профиль": return "Create profile";
            case "Consumer WARP будет создан и проверен перед сохранением.": return "Consumer WARP will be created and checked before saving.";
            case "Создание ключей, регистрация и проверка профиля…": return "Creating keys, registering and checking profile…";
            case "Создание профиля запущено": return "Profile creation started";
            case "Профиль создан и проверен": return "Profile created and checked";
            case "Ошибка создания": return "Creation error";
            case "Профиль создан": return "Profile created";
            case "Amnezia bootstrap Worker — один hostname на строку": return "Amnezia bootstrap Worker — one hostname per line";
            case "Proxy Worker — один hostname на строку": return "Proxy Worker — one hostname per line";
            case "Работает": return "Running";
            case "Запускается": return "Starting";
            case "Выключен": return "Disabled";
            case "Ограничено": return "Degraded";
            case "Остановлен": return "Stopped";
            case "Не настроен": return "Not configured";
            case "не проверен": return "not checked";
            case "работает": return "working";
            case "Встроенный fallback": return "Built-in fallback";
            case "не проверено": return "not checked";
            case "настроено": return "configured";
            case "только что": return "just now";
            case "мин назад": return "min ago";
            case "ч назад": return "h ago";
            case "встроенных": return "built-in";
            default: return key;
        }
    }

    private static String trRu(String key) { return key; }
    private static String trUk(String key) {
        switch (key) {
            case "Встроенный прокси": return "Вбудований проксі";
            case "Использовать встроенный прокси": return "Використовувати вбудований проксі";
            case "Локальный MTProto-прокси для Telegram": return "Локальний MTProto-проксі для Telegram";
            case "Состояние": return "Стан";
            case "Встроенный прокси выключен": return "Вбудований проксі вимкнено";
            case "Автоматически": return "Автоматично";
            case "не выбран": return "не вибрано";
            case "Активный backend": return "Активний backend";
            case "Backend": return "Backend";
            case "Ошибка runtime": return "Помилка runtime";
            case "Режим маршрута": return "Режим маршруту";
            case "Напрямую": return "Напряму";
            case "МАРШРУТИЗАЦИЯ": return "МАРШРУТИЗАЦІЯ";
            case "Мобильная сеть": return "Мобільна мережа";
            case "Домены": return "Домени";
            case "Свои домены — один hostname на строку": return "Власні домени — один hostname у рядку";
            case "Обновить и проверить домены": return "Оновити й перевірити домени";
            case "Обновление списка и проверка доменов…": return "Оновлення списку та перевірка доменів…";
            case "Проверка доменов запущена": return "Перевірку доменів запущено";
            case "Проверка завершена": return "Перевірку завершено";
            case "Ошибка проверки": return "Помилка перевірки";
            case "Готово к обновлению и проверке": return "Готово до оновлення та перевірки";
            case "Профиль": return "Профіль";
            case "Создать": return "Створити";
            case "Импорт .conf": return "Імпорт .conf";
            case "Экспорт .conf": return "Експорт .conf";
            case "Удалить": return "Видалити";
            case "Статус": return "Статус";
            case "Готово": return "Готово";
            case "Для прокси": return "Для проксі";
            case "Для Amnezia": return "Для Amnezia";
            case "СВОИ WORKER": return "ВЛАСНІ WORKER";
            case "Адреса Worker": return "Адреси Worker";
            case "Worker — один hostname на строку": return "Worker — один hostname у рядку";
            case "Сохранить и проверить Worker": return "Зберегти й перевірити Worker";
            case "Импорт и проверка профиля…": return "Імпорт і перевірка профілю…";
            case "Профиль импортирован и проверен": return "Профіль імпортовано й перевірено";
            case "Ошибка импорта": return "Помилка імпорту";
            case "Профиль импортирован": return "Профіль імпортовано";
            case "Экспорт профиля…": return "Експорт профілю…";
            case "Профиль экспортирован": return "Профіль експортовано";
            case "Ошибка экспорта": return "Помилка експорту";
            case "Ожидание выбора .conf…": return "Очікування вибору .conf…";
            case "Ожидание места сохранения…": return "Очікування місця збереження…";
            case "Удалить WARP / AmneziaWG профиль?": return "Видалити профіль WARP / AmneziaWG?";
            case "Сохранённый конфиг будет удалён с устройства.": return "Збережений конфіг буде видалено з пристрою.";
            case "Удаление профиля…": return "Видалення профілю…";
            case "Профиль удалён": return "Профіль видалено";
            case "Ошибка удаления": return "Помилка видалення";
            case "Отмена": return "Скасувати";
            case "Создать профиль": return "Створити профіль";
            case "Consumer WARP будет создан и проверен перед сохранением.": return "Consumer WARP буде створено й перевірено перед збереженням.";
            case "Создание ключей, регистрация и проверка профиля…": return "Створення ключів, реєстрація та перевірка профілю…";
            case "Создание профиля запущено": return "Створення профілю запущено";
            case "Профиль создан и проверен": return "Профіль створено й перевірено";
            case "Ошибка создания": return "Помилка створення";
            case "Профиль создан": return "Профіль створено";
            case "Amnezia bootstrap Worker — один hostname на строку": return "Amnezia bootstrap Worker — один hostname у рядку";
            case "Proxy Worker — один hostname на строку": return "Proxy Worker — один hostname у рядку";
            case "Работает": return "Працює";
            case "Запускается": return "Запускається";
            case "Выключен": return "Вимкнено";
            case "Ограничено": return "Обмежено";
            case "Остановлен": return "Зупинено";
            case "Не настроен": return "Не налаштовано";
            case "не проверен": return "не перевірено";
            case "работает": return "працює";
            case "Встроенный fallback": return "Вбудований fallback";
            case "не проверено": return "не перевірено";
            case "настроено": return "налаштовано";
            case "только что": return "щойно";
            case "мин назад": return "хв тому";
            case "ч назад": return "год тому";
            case "встроенных": return "вбудованих";
            default: return trEn(key);
        }
    }

    private static String trDe(String key) {
        switch (key) {
            case "Встроенный прокси": return "Integrierter Proxy";
            case "Использовать встроенный прокси": return "Integrierten Proxy verwenden";
            case "Локальный MTProto-прокси для Telegram": return "Lokaler MTProto-Proxy für Telegram";
            case "Состояние": return "Status";
            case "Встроенный прокси выключен": return "Integrierter Proxy ist deaktiviert";
            case "Автоматически": return "Automatisch";
            case "не выбран": return "nicht ausgewählt";
            case "Активный backend": return "Aktives Backend";
            case "Backend": return "Backend";
            case "Ошибка runtime": return "Laufzeitfehler";
            case "Режим маршрута": return "Routing-Modus";
            case "Напрямую": return "Direkt";
            case "МАРШРУТИЗАЦИЯ": return "ROUTING";
            case "Мобильная сеть": return "Mobilfunknetz";
            case "Домены": return "Domains";
            case "Свои домены — один hostname на строку": return "Eigene Domains — ein Hostname pro Zeile";
            case "Обновить и проверить домены": return "Domains aktualisieren und prüfen";
            case "Обновление списка и проверка доменов…": return "Liste wird aktualisiert und Domains werden geprüft…";
            case "Проверка доменов запущена": return "Domain-Prüfung gestartet";
            case "Проверка завершена": return "Prüfung abgeschlossen";
            case "Ошибка проверки": return "Prüfung fehlgeschlagen";
            case "Готово к обновлению и проверке": return "Bereit zum Aktualisieren und Prüfen";
            case "Профиль": return "Profil";
            case "Создать": return "Erstellen";
            case "Импорт .conf": return "Import .conf";
            case "Экспорт .conf": return "Export .conf";
            case "Удалить": return "Löschen";
            case "Статус": return "Status";
            case "Готово": return "Bereit";
            case "Для прокси": return "Für Proxy";
            case "Для Amnezia": return "Für Amnezia";
            case "СВОИ WORKER": return "EIGENE WORKER";
            case "Адреса Worker": return "Worker-Adressen";
            case "Worker — один hostname на строку": return "Worker — ein Hostname pro Zeile";
            case "Сохранить и проверить Worker": return "Worker speichern und prüfen";
            case "Импорт и проверка профиля…": return "Profil wird importiert und geprüft…";
            case "Профиль импортирован и проверен": return "Profil importiert und geprüft";
            case "Ошибка импорта": return "Importfehler";
            case "Профиль импортирован": return "Profil importiert";
            case "Экспорт профиля…": return "Profil wird exportiert…";
            case "Профиль экспортирован": return "Profil exportiert";
            case "Ошибка экспорта": return "Exportfehler";
            case "Ожидание выбора .conf…": return "Warte auf .conf-Auswahl…";
            case "Ожидание места сохранения…": return "Warte auf Speicherort…";
            case "Удалить WARP / AmneziaWG профиль?": return "WARP-/AmneziaWG-Profil löschen?";
            case "Сохранённый конфиг будет удалён с устройства.": return "Die gespeicherte Konfiguration wird vom Gerät gelöscht.";
            case "Удаление профиля…": return "Profil wird gelöscht…";
            case "Профиль удалён": return "Profil gelöscht";
            case "Ошибка удаления": return "Löschfehler";
            case "Отмена": return "Abbrechen";
            case "Создать профиль": return "Profil erstellen";
            case "Consumer WARP будет создан и проверен перед сохранением.": return "Consumer WARP wird vor dem Speichern erstellt und geprüft.";
            case "Создание ключей, регистрация и проверка профиля…": return "Schlüssel werden erstellt, Profil registriert und geprüft…";
            case "Создание профиля запущено": return "Profilerstellung gestartet";
            case "Профиль создан и проверен": return "Profil erstellt und geprüft";
            case "Ошибка создания": return "Erstellungsfehler";
            case "Профиль создан": return "Profil erstellt";
            case "Amnezia bootstrap Worker — один hostname на строку": return "Amnezia-Bootstrap-Worker — ein Hostname pro Zeile";
            case "Proxy Worker — один hostname на строку": return "Proxy-Worker — ein Hostname pro Zeile";
            case "Работает": return "Läuft";
            case "Запускается": return "Startet";
            case "Выключен": return "Deaktiviert";
            case "Ограничено": return "Eingeschränkt";
            case "Остановлен": return "Gestoppt";
            case "Не настроен": return "Nicht konfiguriert";
            case "не проверен": return "nicht geprüft";
            case "работает": return "funktioniert";
            case "Встроенный fallback": return "Integrierter Fallback";
            case "не проверено": return "nicht geprüft";
            case "настроено": return "konfiguriert";
            case "только что": return "gerade eben";
            case "мин назад": return "Min. her";
            case "ч назад": return "Std. her";
            case "встроенных": return "integriert";
            default: return trEn(key);
        }
    }

    private static String trEs(String key) {
        switch (key) {
            case "Встроенный прокси": return "Proxy integrado";
            case "Использовать встроенный прокси": return "Usar proxy integrado";
            case "Локальный MTProto-прокси для Telegram": return "Proxy MTProto local para Telegram";
            case "Состояние": return "Estado";
            case "Встроенный прокси выключен": return "El proxy integrado está desactivado";
            case "Автоматически": return "Automático";
            case "не выбран": return "sin seleccionar";
            case "Активный backend": return "Backend activo";
            case "Backend": return "Backend";
            case "Ошибка runtime": return "Error de ejecución";
            case "Режим маршрута": return "Modo de ruta";
            case "Напрямую": return "Directo";
            case "МАРШРУТИЗАЦИЯ": return "ENRUTAMIENTO";
            case "Мобильная сеть": return "Red móvil";
            case "Домены": return "Dominios";
            case "Свои домены — один hostname на строку": return "Dominios propios — un hostname por línea";
            case "Обновить и проверить домены": return "Actualizar y comprobar dominios";
            case "Обновление списка и проверка доменов…": return "Actualizando lista y comprobando dominios…";
            case "Проверка доменов запущена": return "Comprobación de dominios iniciada";
            case "Проверка завершена": return "Comprobación completada";
            case "Ошибка проверки": return "Error de comprobación";
            case "Готово к обновлению и проверке": return "Listo para actualizar y comprobar";
            case "Профиль": return "Perfil";
            case "Создать": return "Crear";
            case "Импорт .conf": return "Importar .conf";
            case "Экспорт .conf": return "Exportar .conf";
            case "Удалить": return "Eliminar";
            case "Статус": return "Estado";
            case "Готово": return "Listo";
            case "Для прокси": return "Para proxy";
            case "Для Amnezia": return "Para Amnezia";
            case "СВОИ WORKER": return "WORKERS PROPIOS";
            case "Адреса Worker": return "Direcciones Worker";
            case "Worker — один hostname на строку": return "Worker — un hostname por línea";
            case "Сохранить и проверить Worker": return "Guardar y comprobar Worker";
            case "Импорт и проверка профиля…": return "Importando y comprobando perfil…";
            case "Профиль импортирован и проверен": return "Perfil importado y comprobado";
            case "Ошибка импорта": return "Error de importación";
            case "Профиль импортирован": return "Perfil importado";
            case "Экспорт профиля…": return "Exportando perfil…";
            case "Профиль экспортирован": return "Perfil exportado";
            case "Ошибка экспорта": return "Error de exportación";
            case "Ожидание выбора .conf…": return "Esperando selección de .conf…";
            case "Ожидание места сохранения…": return "Esperando ubicación de guardado…";
            case "Удалить WARP / AmneziaWG профиль?": return "¿Eliminar perfil WARP / AmneziaWG?";
            case "Сохранённый конфиг будет удалён с устройства.": return "La configuración guardada se eliminará del dispositivo.";
            case "Удаление профиля…": return "Eliminando perfil…";
            case "Профиль удалён": return "Perfil eliminado";
            case "Ошибка удаления": return "Error al eliminar";
            case "Отмена": return "Cancelar";
            case "Создать профиль": return "Crear perfil";
            case "Consumer WARP будет создан и проверен перед сохранением.": return "Consumer WARP se creará y comprobará antes de guardar.";
            case "Создание ключей, регистрация и проверка профиля…": return "Creando claves, registrando y comprobando perfil…";
            case "Создание профиля запущено": return "Creación de perfil iniciada";
            case "Профиль создан и проверен": return "Perfil creado y comprobado";
            case "Ошибка создания": return "Error de creación";
            case "Профиль создан": return "Perfil creado";
            case "Amnezia bootstrap Worker — один hostname на строку": return "Amnezia bootstrap Worker — un hostname por línea";
            case "Proxy Worker — один hostname на строку": return "Proxy Worker — un hostname por línea";
            case "Работает": return "Funciona";
            case "Запускается": return "Iniciando";
            case "Выключен": return "Desactivado";
            case "Ограничено": return "Limitado";
            case "Остановлен": return "Detenido";
            case "Не настроен": return "Sin configurar";
            case "не проверен": return "sin comprobar";
            case "работает": return "funciona";
            case "Встроенный fallback": return "Fallback integrado";
            case "не проверено": return "sin comprobar";
            case "настроено": return "configurado";
            case "только что": return "ahora mismo";
            case "мин назад": return "min atrás";
            case "ч назад": return "h atrás";
            case "встроенных": return "integrados";
            default: return trEn(key);
        }
    }

    private static String trIt(String key) {
        switch (key) {
            case "Встроенный прокси": return "Proxy integrato";
            case "Использовать встроенный прокси": return "Usa proxy integrato";
            case "Локальный MTProto-прокси для Telegram": return "Proxy MTProto locale per Telegram";
            case "Состояние": return "Stato";
            case "Встроенный прокси выключен": return "Il proxy integrato è disattivato";
            case "Автоматически": return "Automatico";
            case "не выбран": return "non selezionato";
            case "Активный backend": return "Backend attivo";
            case "Backend": return "Backend";
            case "Ошибка runtime": return "Errore runtime";
            case "Режим маршрута": return "Modalità percorso";
            case "Напрямую": return "Diretto";
            case "МАРШРУТИЗАЦИЯ": return "INSTRADAMENTO";
            case "Мобильная сеть": return "Rete mobile";
            case "Домены": return "Domini";
            case "Свои домены — один hostname на строку": return "Domini personalizzati — un hostname per riga";
            case "Обновить и проверить домены": return "Aggiorna e verifica domini";
            case "Обновление списка и проверка доменов…": return "Aggiornamento elenco e verifica domini…";
            case "Проверка доменов запущена": return "Verifica domini avviata";
            case "Проверка завершена": return "Verifica completata";
            case "Ошибка проверки": return "Errore di verifica";
            case "Готово к обновлению и проверке": return "Pronto per aggiornare e verificare";
            case "Профиль": return "Profilo";
            case "Создать": return "Crea";
            case "Импорт .conf": return "Importa .conf";
            case "Экспорт .conf": return "Esporta .conf";
            case "Удалить": return "Elimina";
            case "Статус": return "Stato";
            case "Готово": return "Pronto";
            case "Для прокси": return "Per proxy";
            case "Для Amnezia": return "Per Amnezia";
            case "СВОИ WORKER": return "WORKER PERSONALIZZATI";
            case "Адреса Worker": return "Indirizzi Worker";
            case "Worker — один hostname на строку": return "Worker — un hostname per riga";
            case "Сохранить и проверить Worker": return "Salva e verifica Worker";
            case "Импорт и проверка профиля…": return "Importazione e verifica profilo…";
            case "Профиль импортирован и проверен": return "Profilo importato e verificato";
            case "Ошибка импорта": return "Errore di importazione";
            case "Профиль импортирован": return "Profilo importato";
            case "Экспорт профиля…": return "Esportazione profilo…";
            case "Профиль экспортирован": return "Profilo esportato";
            case "Ошибка экспорта": return "Errore di esportazione";
            case "Ожидание выбора .conf…": return "In attesa della selezione .conf…";
            case "Ожидание места сохранения…": return "In attesa della posizione di salvataggio…";
            case "Удалить WARP / AmneziaWG профиль?": return "Eliminare il profilo WARP / AmneziaWG?";
            case "Сохранённый конфиг будет удалён с устройства.": return "La configurazione salvata verrà rimossa dal dispositivo.";
            case "Удаление профиля…": return "Eliminazione profilo…";
            case "Профиль удалён": return "Profilo eliminato";
            case "Ошибка удаления": return "Errore di eliminazione";
            case "Отмена": return "Annulla";
            case "Создать профиль": return "Crea profilo";
            case "Consumer WARP будет создан и проверен перед сохранением.": return "Consumer WARP verrà creato e verificato prima del salvataggio.";
            case "Создание ключей, регистрация и проверка профиля…": return "Creazione chiavi, registrazione e verifica profilo…";
            case "Создание профиля запущено": return "Creazione profilo avviata";
            case "Профиль создан и проверен": return "Profilo creato e verificato";
            case "Ошибка создания": return "Errore di creazione";
            case "Профиль создан": return "Profilo creato";
            case "Amnezia bootstrap Worker — один hostname на строку": return "Amnezia bootstrap Worker — un hostname per riga";
            case "Proxy Worker — один hostname на строку": return "Proxy Worker — un hostname per riga";
            case "Работает": return "Funziona";
            case "Запускается": return "Avvio";
            case "Выключен": return "Disattivato";
            case "Ограничено": return "Limitato";
            case "Остановлен": return "Arrestato";
            case "Не настроен": return "Non configurato";
            case "не проверен": return "non verificato";
            case "работает": return "funziona";
            case "Встроенный fallback": return "Fallback integrato";
            case "не проверено": return "non verificato";
            case "настроено": return "configurato";
            case "только что": return "adesso";
            case "мин назад": return "min fa";
            case "ч назад": return "h fa";
            case "встроенных": return "integrati";
            default: return trEn(key);
        }
    }

    private static String trNl(String key) {
        switch (key) {
            case "Встроенный прокси": return "Ingebouwde proxy";
            case "Использовать встроенный прокси": return "Ingebouwde proxy gebruiken";
            case "Локальный MTProto-прокси для Telegram": return "Lokale MTProto-proxy voor Telegram";
            case "Состояние": return "Status";
            case "Встроенный прокси выключен": return "Ingebouwde proxy is uitgeschakeld";
            case "Автоматически": return "Automatisch";
            case "не выбран": return "niet geselecteerd";
            case "Активный backend": return "Actieve backend";
            case "Backend": return "Backend";
            case "Ошибка runtime": return "Runtimefout";
            case "Режим маршрута": return "Routeringsmodus";
            case "Напрямую": return "Direct";
            case "МАРШРУТИЗАЦИЯ": return "ROUTERING";
            case "Мобильная сеть": return "Mobiel netwerk";
            case "Домены": return "Domeinen";
            case "Свои домены — один hostname на строку": return "Eigen domeinen — één hostname per regel";
            case "Обновить и проверить домены": return "Domeinen bijwerken en controleren";
            case "Обновление списка и проверка доменов…": return "Lijst bijwerken en domeinen controleren…";
            case "Проверка доменов запущена": return "Domeincontrole gestart";
            case "Проверка завершена": return "Controle voltooid";
            case "Ошибка проверки": return "Controle mislukt";
            case "Готово к обновлению и проверке": return "Klaar om bij te werken en te controleren";
            case "Профиль": return "Profiel";
            case "Создать": return "Maken";
            case "Импорт .conf": return "Importeer .conf";
            case "Экспорт .conf": return "Exporteer .conf";
            case "Удалить": return "Verwijderen";
            case "Статус": return "Status";
            case "Готово": return "Gereed";
            case "Для прокси": return "Voor proxy";
            case "Для Amnezia": return "Voor Amnezia";
            case "СВОИ WORKER": return "EIGEN WORKERS";
            case "Адреса Worker": return "Worker-adressen";
            case "Worker — один hostname на строку": return "Worker — één hostname per regel";
            case "Сохранить и проверить Worker": return "Worker opslaan en controleren";
            case "Импорт и проверка профиля…": return "Profiel importeren en controleren…";
            case "Профиль импортирован и проверен": return "Profiel geïmporteerd en gecontroleerd";
            case "Ошибка импорта": return "Importfout";
            case "Профиль импортирован": return "Profiel geïmporteerd";
            case "Экспорт профиля…": return "Profiel exporteren…";
            case "Профиль экспортирован": return "Profiel geëxporteerd";
            case "Ошибка экспорта": return "Exportfout";
            case "Ожидание выбора .conf…": return "Wachten op .conf-selectie…";
            case "Ожидание места сохранения…": return "Wachten op opslaglocatie…";
            case "Удалить WARP / AmneziaWG профиль?": return "WARP-/AmneziaWG-profiel verwijderen?";
            case "Сохранённый конфиг будет удалён с устройства.": return "De opgeslagen configuratie wordt van het apparaat verwijderd.";
            case "Удаление профиля…": return "Profiel verwijderen…";
            case "Профиль удалён": return "Profiel verwijderd";
            case "Ошибка удаления": return "Verwijderfout";
            case "Отмена": return "Annuleren";
            case "Создать профиль": return "Profiel maken";
            case "Consumer WARP будет создан и проверен перед сохранением.": return "Consumer WARP wordt gemaakt en gecontroleerd vóór opslaan.";
            case "Создание ключей, регистрация и проверка профиля…": return "Sleutels maken, registreren en profiel controleren…";
            case "Создание профиля запущено": return "Profiel maken gestart";
            case "Профиль создан и проверен": return "Profiel gemaakt en gecontroleerd";
            case "Ошибка создания": return "Maakfout";
            case "Профиль создан": return "Profiel gemaakt";
            case "Amnezia bootstrap Worker — один hostname на строку": return "Amnezia bootstrap Worker — één hostname per regel";
            case "Proxy Worker — один hostname на строку": return "Proxy Worker — één hostname per regel";
            case "Работает": return "Actief";
            case "Запускается": return "Starten";
            case "Выключен": return "Uitgeschakeld";
            case "Ограничено": return "Beperkt";
            case "Остановлен": return "Gestopt";
            case "Не настроен": return "Niet ingesteld";
            case "не проверен": return "niet gecontroleerd";
            case "работает": return "werkt";
            case "Встроенный fallback": return "Ingebouwde fallback";
            case "не проверено": return "niet gecontroleerd";
            case "настроено": return "ingesteld";
            case "только что": return "zojuist";
            case "мин назад": return "min geleden";
            case "ч назад": return "u geleden";
            case "встроенных": return "ingebouwd";
            default: return trEn(key);
        }
    }

    private static String trPtBr(String key) {
        switch (key) {
            case "Встроенный прокси": return "Proxy integrado";
            case "Использовать встроенный прокси": return "Usar proxy integrado";
            case "Локальный MTProto-прокси для Telegram": return "Proxy MTProto local para o Telegram";
            case "Состояние": return "Estado";
            case "Встроенный прокси выключен": return "O proxy integrado está desativado";
            case "Автоматически": return "Automático";
            case "не выбран": return "não selecionado";
            case "Активный backend": return "Backend ativo";
            case "Backend": return "Backend";
            case "Ошибка runtime": return "Erro de execução";
            case "Режим маршрута": return "Modo de rota";
            case "Напрямую": return "Direto";
            case "МАРШРУТИЗАЦИЯ": return "ROTEAMENTO";
            case "Мобильная сеть": return "Rede móvel";
            case "Домены": return "Domínios";
            case "Свои домены — один hostname на строку": return "Domínios próprios — um hostname por linha";
            case "Обновить и проверить домены": return "Atualizar e verificar domínios";
            case "Обновление списка и проверка доменов…": return "Atualizando lista e verificando domínios…";
            case "Проверка доменов запущена": return "Verificação de domínios iniciada";
            case "Проверка завершена": return "Verificação concluída";
            case "Ошибка проверки": return "Falha na verificação";
            case "Готово к обновлению и проверке": return "Pronto para atualizar e verificar";
            case "Профиль": return "Perfil";
            case "Создать": return "Criar";
            case "Импорт .conf": return "Importar .conf";
            case "Экспорт .conf": return "Exportar .conf";
            case "Удалить": return "Excluir";
            case "Статус": return "Status";
            case "Готово": return "Pronto";
            case "Для прокси": return "Para proxy";
            case "Для Amnezia": return "Para Amnezia";
            case "СВОИ WORKER": return "WORKERS PRÓPRIOS";
            case "Адреса Worker": return "Endereços Worker";
            case "Worker — один hostname на строку": return "Worker — um hostname por linha";
            case "Сохранить и проверить Worker": return "Salvar e verificar Worker";
            case "Импорт и проверка профиля…": return "Importando e verificando perfil…";
            case "Профиль импортирован и проверен": return "Perfil importado e verificado";
            case "Ошибка импорта": return "Erro de importação";
            case "Профиль импортирован": return "Perfil importado";
            case "Экспорт профиля…": return "Exportando perfil…";
            case "Профиль экспортирован": return "Perfil exportado";
            case "Ошибка экспорта": return "Erro de exportação";
            case "Ожидание выбора .conf…": return "Aguardando seleção de .conf…";
            case "Ожидание места сохранения…": return "Aguardando local para salvar…";
            case "Удалить WARP / AmneziaWG профиль?": return "Excluir perfil WARP / AmneziaWG?";
            case "Сохранённый конфиг будет удалён с устройства.": return "A configuração salva será removida do dispositivo.";
            case "Удаление профиля…": return "Excluindo perfil…";
            case "Профиль удалён": return "Perfil excluído";
            case "Ошибка удаления": return "Erro ao excluir";
            case "Отмена": return "Cancelar";
            case "Создать профиль": return "Criar perfil";
            case "Consumer WARP будет создан и проверен перед сохранением.": return "O Consumer WARP será criado e verificado antes de salvar.";
            case "Создание ключей, регистрация и проверка профиля…": return "Criando chaves, registrando e verificando perfil…";
            case "Создание профиля запущено": return "Criação de perfil iniciada";
            case "Профиль создан и проверен": return "Perfil criado e verificado";
            case "Ошибка создания": return "Erro de criação";
            case "Профиль создан": return "Perfil criado";
            case "Amnezia bootstrap Worker — один hostname на строку": return "Amnezia bootstrap Worker — um hostname por linha";
            case "Proxy Worker — один hostname на строку": return "Proxy Worker — um hostname por linha";
            case "Работает": return "Funcionando";
            case "Запускается": return "Iniciando";
            case "Выключен": return "Desativado";
            case "Ограничено": return "Limitado";
            case "Остановлен": return "Parado";
            case "Не настроен": return "Não configurado";
            case "не проверен": return "não verificado";
            case "работает": return "funcionando";
            case "Встроенный fallback": return "Fallback integrado";
            case "не проверено": return "não verificado";
            case "настроено": return "configurado";
            case "только что": return "agora";
            case "мин назад": return "min atrás";
            case "ч назад": return "h atrás";
            case "встроенных": return "integrados";
            default: return trEn(key);
        }
    }

    private static String trAr(String key) {
        switch (key) {
            case "Встроенный прокси": return "الوكيل المدمج";
            case "Использовать встроенный прокси": return "استخدام الوكيل المدمج";
            case "Локальный MTProto-прокси для Telegram": return "وكيل MTProto محلي لتيليجرام";
            case "Состояние": return "الحالة";
            case "Встроенный прокси выключен": return "الوكيل المدمج معطل";
            case "Автоматически": return "تلقائي";
            case "не выбран": return "غير محدد";
            case "Активный backend": return "الخلفية النشطة";
            case "Backend": return "الخلفية";
            case "Ошибка runtime": return "خطأ وقت التشغيل";
            case "Режим маршрута": return "وضع المسار";
            case "Напрямую": return "مباشر";
            case "МАРШРУТИЗАЦИЯ": return "التوجيه";
            case "Мобильная сеть": return "شبكة الهاتف";
            case "Домены": return "النطاقات";
            case "Свои домены — один hostname на строку": return "نطاقات مخصصة — اسم مضيف واحد لكل سطر";
            case "Обновить и проверить домены": return "تحديث وفحص النطاقات";
            case "Обновление списка и проверка доменов…": return "جارٍ تحديث القائمة وفحص النطاقات…";
            case "Проверка доменов запущена": return "بدأ فحص النطاقات";
            case "Проверка завершена": return "اكتمل الفحص";
            case "Ошибка проверки": return "فشل الفحص";
            case "Готово к обновлению и проверке": return "جاهز للتحديث والفحص";
            case "Профиль": return "الملف";
            case "Создать": return "إنشاء";
            case "Импорт .conf": return "استيراد .conf";
            case "Экспорт .conf": return "تصدير .conf";
            case "Удалить": return "حذف";
            case "Статус": return "الحالة";
            case "Готово": return "جاهز";
            case "Для прокси": return "للوسيط";
            case "Для Amnezia": return "لـ Amnezia";
            case "СВОИ WORKER": return "WORKER مخصصة";
            case "Адреса Worker": return "عناوين Worker";
            case "Worker — один hostname на строку": return "Worker — اسم مضيف واحد لكل سطر";
            case "Сохранить и проверить Worker": return "حفظ وفحص Worker";
            case "Импорт и проверка профиля…": return "جارٍ استيراد الملف وفحصه…";
            case "Профиль импортирован и проверен": return "تم استيراد الملف وفحصه";
            case "Ошибка импорта": return "خطأ في الاستيراد";
            case "Профиль импортирован": return "تم استيراد الملف";
            case "Экспорт профиля…": return "جارٍ تصدير الملف…";
            case "Профиль экспортирован": return "تم تصدير الملف";
            case "Ошибка экспорта": return "خطأ في التصدير";
            case "Ожидание выбора .conf…": return "بانتظار اختيار .conf…";
            case "Ожидание места сохранения…": return "بانتظار مكان الحفظ…";
            case "Удалить WARP / AmneziaWG профиль?": return "حذف ملف WARP / AmneziaWG؟";
            case "Сохранённый конфиг будет удалён с устройства.": return "سيتم حذف الإعداد المحفوظ من الجهاز.";
            case "Удаление профиля…": return "جارٍ حذف الملف…";
            case "Профиль удалён": return "تم حذف الملف";
            case "Ошибка удаления": return "خطأ في الحذف";
            case "Отмена": return "إلغاء";
            case "Создать профиль": return "إنشاء ملف";
            case "Consumer WARP будет создан и проверен перед сохранением.": return "سيتم إنشاء Consumer WARP وفحصه قبل الحفظ.";
            case "Создание ключей, регистрация и проверка профиля…": return "جارٍ إنشاء المفاتيح والتسجيل وفحص الملف…";
            case "Создание профиля запущено": return "بدأ إنشاء الملف";
            case "Профиль создан и проверен": return "تم إنشاء الملف وفحصه";
            case "Ошибка создания": return "خطأ في الإنشاء";
            case "Профиль создан": return "تم إنشاء الملف";
            case "Amnezia bootstrap Worker — один hostname на строку": return "Amnezia bootstrap Worker — اسم مضيف واحد لكل سطر";
            case "Proxy Worker — один hostname на строку": return "Proxy Worker — اسم مضيف واحد لكل سطر";
            case "Работает": return "يعمل";
            case "Запускается": return "جارٍ البدء";
            case "Выключен": return "معطل";
            case "Ограничено": return "محدود";
            case "Остановлен": return "متوقف";
            case "Не настроен": return "غير مهيأ";
            case "не проверен": return "غير مفحوص";
            case "работает": return "يعمل";
            case "Встроенный fallback": return "Fallback مدمج";
            case "не проверено": return "غير مفحوص";
            case "настроено": return "مهيأ";
            case "только что": return "الآن";
            case "мин назад": return "دقيقة مضت";
            case "ч назад": return "ساعة مضت";
            case "встроенных": return "مدمجة";
            default: return trEn(key);
        }
    }

    private static String trKo(String key) {
        switch (key) {
            case "Встроенный прокси": return "내장 프록시";
            case "Использовать встроенный прокси": return "내장 프록시 사용";
            case "Локальный MTProto-прокси для Telegram": return "Telegram용 로컬 MTProto 프록시";
            case "Состояние": return "상태";
            case "Встроенный прокси выключен": return "내장 프록시가 꺼져 있습니다";
            case "Автоматически": return "자동";
            case "не выбран": return "선택되지 않음";
            case "Активный backend": return "활성 백엔드";
            case "Backend": return "백엔드";
            case "Ошибка runtime": return "런타임 오류";
            case "Режим маршрута": return "라우팅 모드";
            case "Напрямую": return "직접";
            case "МАРШРУТИЗАЦИЯ": return "라우팅";
            case "Мобильная сеть": return "모바일 네트워크";
            case "Домены": return "도메인";
            case "Свои домены — один hostname на строку": return "사용자 도메인 — 줄마다 호스트 이름 하나";
            case "Обновить и проверить домены": return "도메인 업데이트 및 확인";
            case "Обновление списка и проверка доменов…": return "목록 업데이트 및 도메인 확인 중…";
            case "Проверка доменов запущена": return "도메인 확인 시작됨";
            case "Проверка завершена": return "확인 완료";
            case "Ошибка проверки": return "확인 실패";
            case "Готово к обновлению и проверке": return "업데이트 및 확인 준비됨";
            case "Профиль": return "프로필";
            case "Создать": return "생성";
            case "Импорт .conf": return ".conf 가져오기";
            case "Экспорт .conf": return ".conf 내보내기";
            case "Удалить": return "삭제";
            case "Статус": return "상태";
            case "Готово": return "준비됨";
            case "Для прокси": return "프록시용";
            case "Для Amnezia": return "Amnezia용";
            case "СВОИ WORKER": return "사용자 WORKER";
            case "Адреса Worker": return "Worker 주소";
            case "Worker — один hostname на строку": return "Worker — 줄마다 호스트 이름 하나";
            case "Сохранить и проверить Worker": return "Worker 저장 및 확인";
            case "Импорт и проверка профиля…": return "프로필 가져오기 및 확인 중…";
            case "Профиль импортирован и проверен": return "프로필을 가져오고 확인했습니다";
            case "Ошибка импорта": return "가져오기 오류";
            case "Профиль импортирован": return "프로필을 가져왔습니다";
            case "Экспорт профиля…": return "프로필 내보내는 중…";
            case "Профиль экспортирован": return "프로필을 내보냈습니다";
            case "Ошибка экспорта": return "내보내기 오류";
            case "Ожидание выбора .conf…": return ".conf 선택 대기 중…";
            case "Ожидание места сохранения…": return "저장 위치 대기 중…";
            case "Удалить WARP / AmneziaWG профиль?": return "WARP / AmneziaWG 프로필을 삭제할까요?";
            case "Сохранённый конфиг будет удалён с устройства.": return "저장된 구성이 기기에서 삭제됩니다.";
            case "Удаление профиля…": return "프로필 삭제 중…";
            case "Профиль удалён": return "프로필이 삭제되었습니다";
            case "Ошибка удаления": return "삭제 오류";
            case "Отмена": return "취소";
            case "Создать профиль": return "프로필 생성";
            case "Consumer WARP будет создан и проверен перед сохранением.": return "저장 전에 Consumer WARP를 생성하고 확인합니다.";
            case "Создание ключей, регистрация и проверка профиля…": return "키 생성, 등록 및 프로필 확인 중…";
            case "Создание профиля запущено": return "프로필 생성 시작됨";
            case "Профиль создан и проверен": return "프로필 생성 및 확인 완료";
            case "Ошибка создания": return "생성 오류";
            case "Профиль создан": return "프로필이 생성되었습니다";
            case "Amnezia bootstrap Worker — один hostname на строку": return "Amnezia bootstrap Worker — 줄마다 호스트 이름 하나";
            case "Proxy Worker — один hostname на строку": return "Proxy Worker — 줄마다 호스트 이름 하나";
            case "Работает": return "작동 중";
            case "Запускается": return "시작 중";
            case "Выключен": return "꺼짐";
            case "Ограничено": return "제한됨";
            case "Остановлен": return "중지됨";
            case "Не настроен": return "설정되지 않음";
            case "не проверен": return "확인되지 않음";
            case "работает": return "작동 중";
            case "Встроенный fallback": return "내장 fallback";
            case "не проверено": return "확인되지 않음";
            case "настроено": return "설정됨";
            case "только что": return "방금";
            case "мин назад": return "분 전";
            case "ч назад": return "시간 전";
            case "встроенных": return "내장";
            default: return trEn(key);
        }
    }

    private static void toast(Context context, String text) {
        if (context == null || text == null || text.isEmpty()) {
            return;
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
