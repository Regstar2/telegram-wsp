package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import org.telegram.messenger.TgWsProxyController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

public class TgWsProxySettingsActivity extends BaseFragment {
    private static final int REQUEST_IMPORT_AWG = 4107;

    private TextCheckCell enabledCell;
    private TextSettingsCell statusCell;
    private TextSettingsCell routeCell;
    private TextSettingsCell cfSummaryCell;
    private TextSettingsCell awgSummaryCell;
    private TextSettingsCell workerSummaryCell;
    private TextInfoPrivacyCell runtimeInfoCell;
    private TextInfoPrivacyCell cfOperationCell;
    private TextView awgProfileInfoCell;
    private TextInfoPrivacyCell awgOperationCell;
    private EditText cfDomainsEdit;
    private EditText workerEdit;
    private TextView proxyWorkerTypeButton;
    private TextView awgWorkerTypeButton;

    private String proxyWorkerDraft = "";
    private String awgWorkerDraft = "";
    private boolean editingAwgWorkers;
    private String awgOperationStatus = "Готово к созданию или импорту профиля";

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle("Встроенный прокси");
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

        runtimeInfoCell = new TextInfoPrivacyCell(context);
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

        TextInfoPrivacyCell routeInfo = new TextInfoPrivacyCell(context);
        routeInfo.setText("* AWG и Worker используются только когда настроены и доступны.");
        content.addView(routeInfo, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        addHeader(context, content, "CLOUDFLARE PROXY");
        cfSummaryCell = settingsCell(context);
        content.addView(cfSummaryCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        cfDomainsEdit = multilineEditor(context, "Свои домены — один hostname на строку");
        content.addView(wrapEditor(context, cfDomainsEdit), LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 88
        ));

        TextSettingsCell updateDomains = actionCell(context, "Обновить и проверить домены");
        updateDomains.setOnClickListener(v -> {
            String error = TgWsProxyController.setManualCfDomains(context, cfDomainsEdit.getText().toString());
            if (!error.isEmpty()) {
                toast(context, error);
                return;
            }
            updateDomains.setEnabled(false);
            cfOperationCell.setText("Обновление списка и проверка доменов…");
            toast(context, "Проверка доменов запущена");
            TgWsProxyController.updateAndCheckCfDomainsAsync(context, (success, message) -> {
                updateDomains.setEnabled(true);
                cfOperationCell.setText(success
                        ? "Проверка завершена: " + message
                        : "Ошибка проверки: " + message);
                toast(context, success ? "Домены: " + message : message);
                refreshUi(context);
            });
        });
        content.addView(updateDomains, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        cfOperationCell = new TextInfoPrivacyCell(context);
        cfOperationCell.setText("Готово к обновлению и проверке");
        content.addView(cfOperationCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        addShadow(context, content);
        addHeader(context, content, "WARP / AMNEZIAWG");

        awgSummaryCell = settingsCell(context);
        content.addView(awgSummaryCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        awgProfileInfoCell = profileValueText(context);
        content.addView(awgProfileInfoCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        TextSettingsCell createAwgAction = actionCell(context, "Создать автоматически");
        createAwgAction.setOnClickListener(v -> showCreateAwgDialog(context));
        content.addView(createAwgAction, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        TextSettingsCell importAwgAction = actionCell(context, "Импортировать .conf");
        importAwgAction.setOnClickListener(v -> startAwgImport(context));
        content.addView(importAwgAction, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        TextSettingsCell deleteAwgAction = settingsCell(context);
        deleteAwgAction.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        deleteAwgAction.setText("Удалить профиль", false);
        deleteAwgAction.setOnClickListener(v -> confirmDeleteAwgProfile(context));
        content.addView(deleteAwgAction, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        awgOperationCell = new TextInfoPrivacyCell(context);
        awgOperationCell.setText("Статус: " + awgOperationStatus);
        content.addView(awgOperationCell, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT
        ));

        addShadow(context, content);
        addHeader(context, content, "СВОИ WORKER");

        workerSummaryCell = settingsCell(context);
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

        proxyWorkerTypeButton = workerTypeButton(context, "Для прокси");
        proxyWorkerTypeButton.setOnClickListener(v -> switchWorkerEditor(false));
        awgWorkerTypeButton = workerTypeButton(context, "Для Amnezia");
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

        workerEdit = multilineEditor(context, "Worker — один hostname на строку");
        content.addView(wrapEditor(context, workerEdit), LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, 88
        ));

        TextSettingsCell saveWorkers = actionCell(context, "Сохранить и проверить Worker");
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

        TextInfoPrivacyCell workerInfo = new TextInfoPrivacyCell(context);
        workerInfo.setText(
                "Proxy Worker участвуют в маршруте Telegram. Пользовательские Amnezia Worker используются только для создания WARP-профиля; дополнительно всегда доступны 3 встроенных provisioning Worker проекта."
        );
        content.addView(workerInfo, LayoutHelper.createLinear(
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
        if (requestCode != REQUEST_IMPORT_AWG || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null || getContext() == null) {
            return;
        }
        Uri uri = data.getData();
        setAwgOperationStatus("Импорт и проверка профиля…");
        TgWsProxyController.importAwgProfileAsync(
                getContext(),
                uri,
                "Imported WARP",
                (success, message) -> {
                    setAwgOperationStatus(success
                            ? "Профиль импортирован и проверен"
                            : "Ошибка импорта: " + message);
                    toast(getContext(), success ? "Профиль импортирован" : message);
                    refreshUi(getContext());
                }
        );
    }

    private void refreshUi(Context context) {
        TgWsProxyController.UiState state = TgWsProxyController.getUiState(context);
        if (enabledCell != null) {
            enabledCell.setTextAndValueAndCheck(
                    "Использовать встроенный прокси",
                    "Локальный MTProto-прокси для Telegram",
                    state.enabled,
                    false,
                    true
            );
        }
        if (statusCell != null) {
            statusCell.setTextAndValue("Состояние", state.status, true);
        }
        if (runtimeInfoCell != null) {
            if (!state.enabled) {
                runtimeInfoCell.setText("Встроенный прокси выключен");
            } else {
                String backend = state.actualBackend;
                if (backend.isEmpty()) {
                    backend = TgWsProxyController.ROUTE_AUTO.equals(state.routeMode)
                            ? "Автоматически"
                            : "не выбран";
                }
                runtimeInfoCell.setText(state.lastError.isEmpty()
                        ? "Активный backend: " + backend
                        : "Backend: " + backend + "\nОшибка runtime: " + state.lastError);
            }
        }
        if (routeCell != null) {
            routeCell.setTextAndValue(
                    "Режим маршрута",
                    TgWsProxyController.routeLabel(state.routeMode),
                    true
            );
        }
        if (cfSummaryCell != null) {
            cfSummaryCell.setTextAndValue("Домены", state.cfSummary, true);
        }
        if (cfDomainsEdit != null && !cfDomainsEdit.hasFocus()) {
            cfDomainsEdit.setText(state.manualCfDomains);
        }
        if (awgSummaryCell != null) {
            awgSummaryCell.setText("Профиль", false);
        }
        if (awgProfileInfoCell != null) {
            awgProfileInfoCell.setText(state.awgSummary);
        }
        if (awgOperationCell != null) {
            awgOperationCell.setText("Статус: " + awgOperationStatus);
        }
        proxyWorkerDraft = state.proxyWorkers;
        awgWorkerDraft = state.awgWorkers;
        if (workerSummaryCell != null) {
            workerSummaryCell.setTextAndValue("Worker pool", state.workerSummary, true);
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
                "Автоматически",
                "Cloudflare Proxy",
                "WARP / AmneziaWG",
                "Cloudflare Worker",
                "Напрямую"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Режим маршрута");
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
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void startAwgImport(Context context) {
        setAwgOperationStatus("Ожидание выбора .conf…");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_AWG);
    }

    private void confirmDeleteAwgProfile(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("Удалить WARP / AmneziaWG профиль?")
                .setMessage("Сохранённый конфиг будет удалён с устройства.")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    setAwgOperationStatus("Удаление профиля…");
                    TgWsProxyController.deleteAwgProfileAsync(
                            context,
                            (success, message) -> {
                                setAwgOperationStatus(success
                                        ? "Профиль удалён"
                                        : "Ошибка удаления: " + message);
                                toast(context, success ? "Профиль удалён" : message);
                                refreshUi(context);
                            }
                    );
                })
                .setNegativeButton("Отмена", null)
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
                .setTitle("Создать профиль")
                .setMessage("Consumer WARP будет создан и проверен перед сохранением.")
                .setView(container)
                .setPositiveButton("Создать", (dialog, which) -> {
                    setAwgOperationStatus("Создание ключей, регистрация и проверка профиля…");
                    toast(context, "Создание профиля запущено");
                    TgWsProxyController.provisionAwgProfileAsync(
                            context,
                            name.getText().toString(),
                            (success, message) -> {
                                setAwgOperationStatus(success
                                        ? "Профиль создан и проверен"
                                        : "Ошибка создания: " + message);
                                toast(context, success ? "Профиль создан" : message);
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
                ? "Amnezia bootstrap Worker — один hostname на строку"
                : "Proxy Worker — один hostname на строку");
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
            awgOperationCell.setText("Статус: " + awgOperationStatus);
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
        edit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
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

    private static void toast(Context context, String text) {
        if (context == null || text == null || text.isEmpty()) {
            return;
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }
}
