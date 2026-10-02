# Terms of Service for noserver

**Effective date:** 2026-09-21

These terms apply to the **noserver** software library (the "Library", published at
<https://github.com/pvtitov/noserver>) and are addressed to (a) developers who include the
Library in their apps ("Developers") and (b) people who use apps that contain it ("Users"). By
using or distributing the Library, or an app that includes it, you agree to these terms. If you do
not agree, do not use it.

## 1. The Library and its license

The Library is open-source software released under the [MIT License](LICENSE). That license
governs your rights to copy, modify, and distribute the source code. These terms add to it and
describe how the Library may be used. If they conflict, the MIT License controls as to the
license grant, and the disclaimers in both apply to the fullest extent permitted by law.

## 2. What the Library is, and is not

The Library is a client-side helper that lets an Android app store and retrieve a JSON file
through a third-party service the user already has an account with (currently Google Drive;
possibly other providers such as Yandex in the future). It is **not** a hosted service. We operate
no server, do not host or process your data, and have no access to it. See the
[Privacy Policy](PRIVACY_POLICY.md).

## 3. Third-party services

- The Library depends on third-party services and software (for example, Google Drive API,
  Google Sign-In / Credential Manager, and Google Play services). Those services are **not**
  provided by us, and their availability, behavior, quotas, pricing, and terms can change or end
  at any time without notice.
- Your use of a third-party service through the Library is governed by that provider's own terms
  and policies, for example the [Google APIs Terms of
  Service](https://developers.google.com/terms), the [Google API Services User Data
  Policy](https://developers.google.com/terms/api-services-user-data-policy), and Google's Terms
  of Service. You are responsible for complying with them.
- **No affiliation.** The Library is an independent project. It is not created, endorsed,
  sponsored, or approved by Google, Yandex, or any other provider. All product names and
  trademarks belong to their respective owners and are used only to identify the services the
  Library works with.

## 4. No security guarantees

The Library adds **no security, encryption, access control, or privacy protection** beyond what
the underlying third-party service provides. Data the Library reads or writes has only the
protection that service and the User's account offer. We make no promise that data will be
confidential, intact, available, or recoverable.

## 5. Developer responsibilities

If you include the Library in an app, you are solely responsible for:

1. **Your own Google Cloud project and OAuth client.** The Library uses the client ID you supply
   through `NoServer.configure(...)`. You are the party that agrees to Google's terms for that
   project, and you are responsible for any verification, review, or security-assessment
   requirements Google places on the permissions (scopes) your app requests, including the
   Google Drive scope the Library currently requests.
2. **Your own privacy policy and disclosures.** You must provide the notices, policies, and
   user consents that Google, app stores, and applicable law require for your app, and you must
   make them accurate for the way your app actually uses data. This includes describing the use
   of the Library and of the third-party services it connects to. Our Privacy Policy does not
   replace yours.
3. **What you store and share.** You decide what data goes into the file, and whether and with
   whom it is shared (for example through the Library's file-sharing function). You are
   responsible for obtaining any required consent from Users, and for the consequences of
   sharing.
4. **Testing and fitness.** You must review, test, and decide whether the Library is suitable for
   your app before releasing it, including its sign-in flow, error handling, concurrency
   behavior, and logging.
5. **Compliance.** You are responsible for compliance with all laws and third-party terms that
   apply to your app and its Users, wherever they are located.

## 6. User responsibilities

If you use an app that includes the Library, you are responsible for your own account, for the
data you choose to store or share, for the permissions you grant on Google's consent screen, and
for reading the app's own privacy policy and terms. Contact the app's Developer, not us, about
the app.

## 7. Disclaimer of warranties

THE LIBRARY IS PROVIDED **"AS IS" AND "AS AVAILABLE"**, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE,
NON-INFRINGEMENT, SECURITY, ACCURACY, RELIABILITY, AND UNINTERRUPTED OR ERROR-FREE OPERATION. WE
DO NOT WARRANT THAT DATA WILL NOT BE LOST, CORRUPTED, OVERWRITTEN, DISCLOSED, OR MADE ACCESSIBLE
TO OTHERS, OR THAT THE LIBRARY WILL WORK WITH ANY THIRD-PARTY SERVICE OR REMAIN COMPATIBLE WITH
IT.

## 8. Limitation of liability

TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE AUTHOR AND CONTRIBUTORS OF THE LIBRARY WILL NOT BE
LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER LIABILITY, WHETHER IN CONTRACT, TORT, OR OTHERWISE,
ARISING FROM OR RELATED TO THE LIBRARY OR ITS USE, INCLUDING ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES, LOSS OR EXPOSURE OF DATA, LOSS OF PROFITS, ACCOUNT
SUSPENSION OR TERMINATION BY A THIRD-PARTY PROVIDER, OR BUSINESS INTERRUPTION, EVEN IF ADVISED OF
THE POSSIBILITY OF SUCH DAMAGES. SOME JURISDICTIONS DO NOT ALLOW CERTAIN EXCLUSIONS OR
LIMITATIONS, SO PARTS OF THIS SECTION MAY NOT APPLY TO YOU; IN THAT CASE LIABILITY IS LIMITED TO
THE MINIMUM EXTENT PERMITTED BY LAW.

## 9. Indemnification (Developers)

If you are a Developer, you agree to defend and hold harmless the author and contributors of the
Library from any claim, loss, or expense (including reasonable legal fees) arising from your app,
your use or distribution of the Library, your users' data, or your breach of these terms or of any
third-party terms or law.

## 10. No support; changes to the Library

We have no obligation to provide support, maintenance, updates, bug fixes, or backward
compatibility, and may change, suspend, or discontinue the Library, or any feature or provider
integration, at any time.

## 11. Changes to these terms

We may update these terms at any time by changing this file in the repository. The effective date
above shows the latest version. Continuing to use or distribute the Library after a change means
you accept the updated terms.

## 12. General

If any part of these terms is held unenforceable, the rest remains in effect. These terms,
together with the MIT License and the Privacy Policy, are the whole agreement about the Library
between you and its author. Not enforcing a provision is not a waiver of it.

## 13. Contact

Open an issue at <https://github.com/pvtitov/noserver/issues>.
