package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
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
        addHeader(context, content, "МАРШРУТИЗАЦИЯ");

        routeCell = settingsCell(context);
        routeCell.setOnClickListener(v -> showRouteDialog(context));
        content.addView(routeCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        TextCheckCell wifi = valueCell(context, "Wi-Fi", "CF Proxy → AWG* → Worker* → Direct", true);
        content.addView(wifi, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));
        TextCheckCell mobile = valueCell(context, "Мобильная сеть", "CF Proxy → AWG* → Worker*", false);
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
        addHeader(context, content, "СВОИ WORKER");

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
            enabledCell.setTextAndValueAndCheck(
                    tr(context, "Использовать встроенный прокси"),
                    tr(context, "Локальный MTProto-прокси для Telegram"),
                    state.enabled,
                    false,
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
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
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

    private static TextCheckCell valueCell(
            Context context,
            String title,
            String value,
            boolean divider
    ) {
        TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        cell.setTextAndValue(title, value, false, divider);
        return cell;
    }

    private static EditText multilineEditor(Context context, String hint) {
        EditText edit = new EditText(context);
        edit.setGravity(Gravity.TOP | Gravity.LEFT);
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
    private static String trUk(String key) { return translateCompact(key,
            "Вбудований проксі","Використовувати вбудований проксі","Стан","Автоматично","Напряму","Домени","Профіль","Статус","Для проксі","Для Amnezia","Адреси Worker","Зберегти й перевірити Worker","Скасувати","Видалити"); }
    private static String trDe(String key) { return translateCompact(key,
            "Integrierter Proxy","Integrierten Proxy verwenden","Status","Automatisch","Direkt","Domains","Profil","Status","Für Proxy","Für Amnezia","Worker-Adressen","Worker speichern und prüfen","Abbrechen","Löschen"); }
    private static String trEs(String key) { return translateCompact(key,
            "Proxy integrado","Usar proxy integrado","Estado","Automático","Directo","Dominios","Perfil","Estado","Para proxy","Para Amnezia","Direcciones Worker","Guardar y comprobar Worker","Cancelar","Eliminar"); }
    private static String trIt(String key) { return translateCompact(key,
            "Proxy integrato","Usa proxy integrato","Stato","Automatico","Diretto","Domini","Profilo","Stato","Per proxy","Per Amnezia","Indirizzi Worker","Salva e verifica Worker","Annulla","Elimina"); }
    private static String trNl(String key) { return translateCompact(key,
            "Ingebouwde proxy","Ingebouwde proxy gebruiken","Status","Automatisch","Direct","Domeinen","Profiel","Status","Voor proxy","Voor Amnezia","Worker-adressen","Worker opslaan en controleren","Annuleren","Verwijderen"); }
    private static String trPtBr(String key) { return translateCompact(key,
            "Proxy integrado","Usar proxy integrado","Estado","Automático","Direto","Domínios","Perfil","Status","Para proxy","Para Amnezia","Endereços Worker","Salvar e verificar Worker","Cancelar","Excluir"); }
    private static String trAr(String key) { return translateCompact(key,
            "الوكيل المدمج","استخدام الوكيل المدمج","الحالة","تلقائي","مباشر","النطاقات","الملف","الحالة","للوسيط","لـ Amnezia","عناوين Worker","حفظ وفحص Worker","إلغاء","حذف"); }
    private static String trKo(String key) { return translateCompact(key,
            "내장 프록시","내장 프록시 사용","상태","자동","직접","도메인","프로필","상태","프록시용","Amnezia용","Worker 주소","Worker 저장 및 확인","취소","삭제"); }

    private static String translateCompact(String key, String title, String useProxy, String state,
                                           String automatic, String direct, String domains, String profile,
                                           String status, String forProxy, String forAmnezia, String workerAddresses,
                                           String saveWorker, String cancel, String delete) {
        switch (key) {
            case "Встроенный прокси": return title;
            case "Использовать встроенный прокси": return useProxy;
            case "Состояние": return state;
            case "Автоматически": return automatic;
            case "Напрямую": return direct;
            case "Домены": return domains;
            case "Профиль": return profile;
            case "Статус": return status;
            case "Для прокси": return forProxy;
            case "Для Amnezia": return forAmnezia;
            case "Адреса Worker": return workerAddresses;
            case "Сохранить и проверить Worker": return saveWorker;
            case "Отмена": return cancel;
            case "Удалить": return delete;
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
