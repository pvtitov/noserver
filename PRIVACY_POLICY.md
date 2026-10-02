# Privacy Policy for noserver

**Effective date:** 2026-09-22

This policy describes how the **noserver** software library (the "Library", published at
<https://github.com/pvtitov/noserver>) handles information. The Library is open-source software
(MIT License) that is compiled into third-party Android apps ("Apps") by their developers
("Developers"). It is not an app, a website, or a hosted service.

## 1. Summary

- The Library's author ("we") **does not collect, receive, store, sell, or share any information**
  about you. We operate no server, database, analytics, advertising, or crash-reporting service
  for the Library.
- The Library runs **entirely on your device**, inside an App you installed. When it needs to
  read or save data, your device communicates **directly with a third-party service** (currently
  Google; possibly others in the future, see section 5). We are not a party to that communication
  and cannot see it.
- The Library adds **no security or privacy protection of its own**. Whatever protection your data
  has comes from the third-party service and your account with it.

## 2. What the Library does on your device

When an App uses the Library, the Library can, at the App's request:

1. Ask you to sign in with your Google account through Android's system Credential Manager and
   Google's own sign-in and consent screens.
2. Use the Google Drive API, with your permission, to look up, create, download, and overwrite
   one JSON file in **your** Google Drive, whose name and contents are chosen by the App.
3. Read the e-mail address of the Google account you signed in with (used to identify you to the
   App and to locate files).
4. Share that file, at the App's request, with another Google account by granting it read
   access, or read a file that another user has shared with you.
5. Sign you out (clear the credential state kept by Android's Credential Manager).

The Library requests Google's Drive permission ("See, edit, create, and delete all of your Google
Drive files"). The Library's code only works with the single file the App names, but the
permission you grant to the App is broader than that one file. Review the consent screen Google
shows you before you approve it.

## 3. Information the Library itself keeps

- **No persistent storage.** The Library does not write files, preferences, or databases. While
  the App process is running, it keeps the signed-in Google Drive client object in memory; this is
  discarded when the process ends or when you sign out.
- **No transmission to us.** Nothing is sent to the Library's author.
- **Diagnostic logging.** The Library writes diagnostic messages to the Android system log
  (Logcat) — for example, which method was called and whether a file was found. These messages
  are limited by design to method names, booleans, and the App-chosen file name; they do not
  include file content, e-mail addresses, or other Google Drive file metadata. The system log is
  local to your device and is not sent anywhere by the Library, but it can be read by tools
  connected to the device (for example, a computer with USB debugging enabled) and may be
  included in bug reports you choose to share.

## 4. Where your data actually lives

Your data is stored and processed by the third-party service that the App connects you to
(today, Google Drive), and by the App and its Developer. That means:

- **Google** handles your account, your Drive files, and the sign-in and consent screens under
  [Google's Privacy Policy](https://policies.google.com/privacy) and its terms. You can review
  and revoke an App's access at <https://myaccount.google.com/permissions>.
- **The App and its Developer** decide what data is put into the file, why, and for how long. The
  App's own privacy policy governs that. If you have questions about your data in an App, contact
  the Developer, not the Library's author.

## 5. Third-party services, including possible future ones

The Library currently integrates with Google (Google Drive API, Google Sign-In through Android
Credential Manager, and Google Play services). We may add support for other providers in the
future, such as Yandex. If we do, the same principles apply: the Library runs on your device,
your device talks directly to that provider, and we still do not collect your data. That
provider's own terms and privacy policy would then apply to that connection. We will update this
policy when a new provider is added.

We do not control third-party services and are not responsible for their practices, availability,
security, or policy changes.

## 6. Security

The Library **provides no security features of its own**: no encryption, no access control, and
no integrity checking beyond what the third-party service provides. Data is stored in the
third-party service in the form the App writes it (the Library does not encrypt it). Anyone who
can access the relevant account, the shared file, or the device can access that data. If you
share a file with another person, that person can read it. We make no guarantee about the
security of your data.

## 7. Children

The Library is a software component and is not directed at children. Whether an App is
appropriate for children, and any legal obligations that follow, are the responsibility of the
App's Developer.

## 8. Your choices and rights

Because we hold no data about you, we have nothing to access, correct, export, or delete. To
manage your data, use your Google account settings and Drive, or contact the App's Developer.
You can stop the Library's access at any time by signing out in the App or revoking the App's
access in your Google account.

## 9. Changes

We may update this policy. The current version is always the one in the Library's repository, and
the effective date at the top shows when it last changed.

## 10. Contact

Questions about this policy: open an issue at <https://github.com/pvtitov/noserver/issues>.

## 11. Not legal advice; no assumption of responsibility

This policy describes the Library's behavior as we understand it. It is provided for information
only and does not create any obligation, warranty, or liability on the part of the Library's
author. See the [Terms of Service](TERMS_OF_SERVICE.md).
