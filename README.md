# ***Nexus***

A clean, lightweight, safe, and instantly understandable Android interface for senior users with 'possible' digital arrest and inactivity alerts to their younger guardians.

---

## Current Features
- Clean and simple UI (Including the apps commonly used by senior citizens)
- Prevents accidental position change of the apps/deletion of the apps
- Prevents pocket dialing using second-level authentication via voice prompts
- Prevents accidental contact deletion using second-level authentication via voice prompts
- Fraud protection (Digital arrest via video calls) via guardian alerts:
  - Sends an SMS to the guardian regarding continuous activity if the user's phone is active for 3+ hours continuously (suspecting possible digital arrest)
  - Repeats SMS every 1 hour if usage continues
  - It stops sending further SMS messages if there is a discontinuation in the phone's active usage
- Inactivity alerts during waking hours (to detect health concerns with the senior user that prevents them from accessing their phones during waking hours):
  - If inactive for 3+ hours between 7:00 AM and 9:00 PM (local time - 14hrs)
  - Sends alert SMS to guardian regarding inactivity
  - Repeats every 1 hour if inactivity continues
  - Inactivity alerts are not sent during the sleeping (currently 9:00pm to 7:00am)
## *Next Development Steps*
- Allowing the users to add apps according to their preference
- changing the app into a custom Android Launcher
- Allowing the users to change the hours of inactivity alerts
- Adding weather widget
- Adding multilingual support and large symbols for apps
