# Step 8 setup

## 1) Database (Supabase SQL editor)
Run `supabase/09_step8_ledger_and_single_category.sql` once (whole file).

Check (run each ALONE, the editor shows only the last result):
```sql
select provider_credit_limit_minor from public.app_settings;   -- 10000
select provider_id, count(*) from public.provider_services group by 1 having count(*) > 1;  -- 0 rows
```

## 2) Kotlin files (copy into shared/src/commonMain/kotlin/com/servicehub/, overwrite when asked)
New files:
- data/LedgerDtos.kt, data/LedgerRepository.kt
- ui/EarningsScreen.kt, ui/AdminLedgerTab.kt
- ui/theme/Theme.kt
- ui/components/Ui.kt (create the folders theme and components)

Replace these files:
- data/ProviderDtos.kt, data/ProviderRepository.kt
- ui/ProviderScreen.kt, ui/MainScreens.kt, ui/AdminScreen.kt
- model/TimeFormat.kt

## 3) One line in ServiceHubApp.kt
Replace `MaterialTheme {` with `ServiceHubTheme {` and add
`import com.servicehub.ui.theme.ServiceHubTheme`.

## 4) Test
1. Provider: pick ONE trade, enter rate, Save. Switching trade later sends the profile back to "waiting for approval".
2. Admin: approve the provider. Customer: book, provider accepts, completes, marks paid.
3. Provider > Earnings: balance goes up by the commission.
4. Admin > Ledger: set the limit low (e.g. 1) to test, see the provider "Paused", record a payment, provider is live again. Set the limit back to 100.

Run on a FRESH install (uninstall the old app first). Paste any compile error to me.
