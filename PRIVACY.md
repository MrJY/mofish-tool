# MoFish Tool Privacy Policy

Last updated: May 15, 2026

MoFish Tool is designed to run inside your JetBrains IDE and keep user configuration local by default.

## Data Stored Locally

The Plugin stores the following settings in your local IDE configuration:

- Watchlist asset identifiers, such as stock codes, fund codes, and cryptocurrency IDs.
- Holding information entered by you, including quantity, cost price, invested amount, display name, and currency.
- Reminder rules entered by you, including asset identifiers, thresholds, and reminder directions.
- Refresh preferences, sorting preferences, status bar preferences, and selected UI options.
- Optional AI provider configuration entered by you, including API key, base URL, model, and history range.

This data is stored locally by JetBrains IDE settings storage. The Plugin author does not operate a server that receives, stores, or analyzes this data.

## Network Requests

The Plugin sends network requests to retrieve market data from third-party public data providers. Depending on the module you use, these providers may include services such as Tencent, Sina, East Money, CoinGecko, and Bank of China.

When optional AI features are configured and used, requests may be sent to the AI provider endpoint that you configure. This may include the API key and request content required by that provider.

Third-party services may receive technical information normally included in network requests, such as your IP address, user agent, requested asset identifiers, request time, and other protocol metadata. Their handling of this data is governed by their own privacy policies.

## No Analytics or Advertising

The Plugin does not include advertising SDKs, analytics SDKs, telemetry collection, or behavior tracking operated by the Plugin author.

## Data Sharing

The Plugin author does not sell, rent, or share your locally stored plugin settings. Data is shared only when your IDE sends requests to third-party services needed for quote retrieval or optional AI functionality.

## Data Removal

You can remove stored plugin data by deleting watchlists, holdings, reminders, and AI settings in the Plugin settings page, or by uninstalling the Plugin and removing its local IDE settings file.

## Security Notes

If you enter an AI API key, it is stored in local IDE settings. Treat your machine and IDE configuration as sensitive. Rotate the key with your provider if you believe it has been exposed.

## Contact

For privacy questions or issue reports, open an issue at:

https://github.com/MrJY/mofish-tool/issues
