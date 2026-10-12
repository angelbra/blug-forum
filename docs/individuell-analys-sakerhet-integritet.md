# Individuell analys – säkerhet och integritet i Blug Forum

Analysen beskriver säkerhet och integritet i Blug Forums REST-backend.

## Arkitektur, uppgifter och skyddsvärden

En Spring Boot-applikation erbjuder REST och en befintlig Thymeleaf-dashboard. Spring Security använder sessionscookie, BCrypt och CSRF-token. Spring Data JPA lagrar användare, forum, trådar och inlägg i PostgreSQL. Trådägarskap, moderatorkoppling och deltagarkoppling ger objektspecifika rättigheter. Globala administratörer provisioneras direkt till autentiseringens `users`-tabell. Den äldre `UserData`-modellen används inte för API-identitet.

Personuppgifter omfattar användarnamn, email, lösenordshash, publicerad text, skapandetider samt kopplingar mellan användare och innehåll. Sessions-id och driftloggar kan också vara identifierande. Fri text kan innehålla känsliga uppgifter även om applikationen inte efterfrågar dem. Tillgångar är konton, privata diskussioner, rättigheternas korrekthet, innehållets integritet och databasens tillgänglighet.

Angripare kan vara anonyma besökare, en medlem som testar andras id, en moderator som missbrukar åtkomst, en extern webbplats som försöker använda webbläsarens session och någon med stulna lösenord eller en databasdump. Angreppsytor är login/registrering, objekt-id i REST-anrop, användarstyrd JSON, cookies, dashboard-formulär, databasporten och backupfiler. En komprometterad administratör har avsiktligt stor påverkan.

## Tre prioriterade risker

Bedömningen är kvalitativ för en enkel forumtjänst, inte uppmätta frekvenser.

| Prioritet / risk | Sannolikhet och konsekvens | Genomförbar motåtgärd och motivering |
|---|---|---|
| 1. Otillåten objektåtkomst och dataläckage | Hög i den tidigare koden: privata trådar returnerades utan kontroll och entiteter exponerade relationer. Stor konsekvens för privata diskussioner och kontouppgifter. | Hög genomförbarhet: central objektskontroll, DTO-svar, betrodd sessionsidentitet och regressionstester. Implementerat. Grundläggande åtkomstkontroll ger stor nytta utan ny infrastruktur. |
| 2. Oönskade sessionstransaktioner och kontoövertagande | Medelhög: tidigare CSRF avstängt, svaga utvecklingskonton och login saknar hastighetsbegränsning. Stor konsekvens: ändrat/raderat innehåll eller administratörsmissbruk. | CSRF, BCrypt, explicit provisionering och kontroll av blockerade användare vid varje skyddad operation är implementerade till rimlig kostnad. Rate limiting och TLS vid verklig drift är kvarstående rekommendationer. |
| 3. Dataförlust eller läckande backup | Medelhög: enkla lokala behörigheter, permanenta raderingar och inga automatiska backuper. Stor konsekvens för hela tjänsten. | Hög genomförbarhet för enkel pg_dump, skyddad förvaring och provåterläsning i isolerad databas. Instruktioner finns; slutlig verifiering dokumenteras separat. Ingen backupplattform har byggts. |

## Genomförda förbättringar och avvägningar

API:t binder uttryckliga requestfält och returnerar avgränsade DTO:er. Klienten kan inte välja sin roll, författare, ägare eller blockeringsstatus. Databas-id är knutet till sessionsidentiteten, så en gammal session inte får rätt till ett nytt konto med samma användarnamn. Blockering kontrolleras i databasen och gäller även en pågående session. ADMIN kontrolleras också mot aktuell databastilldelning. Ägare är trådens nuvarande ägare, och en global MODERATOR-enum ger ingen generell moderationsrätt.

CSRF är aktivt även för registrering, login och logout. Frontend måste hämta en ny token efter login/logout och bevara cookies; detta ökar integrationsarbetet men skyddar webbläsarsessioner. Inga JWT-, OAuth- eller MFA-system har lagts till. Validering begränsar storleken på fält och felsvar exponerar inte interna undantag. SQL- och säkerhetsdebugloggning är avstängd i standardinställningen.

Privata trådars metadata listas utan innehåll eller medlemsrelationer. Det gör forumet lätt att navigera men innebär att titlar inte får användas för hemlig information. Blockering behåller text för återställning; författarradering tar bort inläggstext medan permanent deletion tar bort databasrader. Kontoradering tar även bort ägda trådar och därmed andras inlägg i dem. Det ger enkel och förutsägbar referensintegritet, men ägarbyte bör göras först om gruppen vill bevara diskussionen.

Profilens användarnamn är oföränderligt för en enkel identitetsmodell. Lösenordsbyte avslutar aktuell session; andra egna sessioner kan fortfarande vara aktiva. Det senare är en kvarstående risk, inte en genomförd funktion för global sessionsåterkallelse. Testerna använder syntetiska uppgifter i en separat databas för att undvika skador på gruppens data.

## GDPR och livscykel

Ändamål: tillhandahålla konto, deltagande i diskussioner och nödvändig moderering. Email kan användas som kontaktuppgift men tjänsten har inget emailflöde; behovet bör prövas innan verklig drift. Ingen extra profilinformation efterfrågas. Hashvärden lämnar inte API:t och email returneras endast i egen profil eller behörig administratörsoperation.

Möjlig rättslig grund måste väljas av den ansvariga organisationen. Avtal kan vara relevant för uppgifter som behövs för en begärd kontotjänst, och berättigat intresse kan behöva prövas för moderering och säkerhet. Detta är möjliga bedömningar, inte fastställda grunder för just denna tjänst. EU-kommissionen beskriver [rättsliga grunder](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/legal-grounds-processing-data_en) och principerna om [ändamål, minimering och lagringstid](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/principles-gdpr_en).

Retention behöver fastställas före verklig användning. För skolprojektet rekommenderas syntetiska data och radering av testdata efter examination. För drift behövs en dokumenterad gräns för inaktiva konton, modereringsunderlag, loggar och backuper. Rekommenderad backupretention är 30 dagar med begränsad åtkomst; det är inte automatiserat. Blockering är inte radering: bevarad text måste omfattas av retention. Självradering tar bort konto och kopplat innehåll enligt guiden, men äldre backuper och eventuella personuppgifter i andras text behöver hanteras separat. Efter återläsning måste tidigare raderingar återappliceras.

## Backup och nästa sprint

`fortsattningsguide.md` innehåller konkreta pg_dump/pg_restore-kommandon för en isolerad databas. Återläsning ska testas utan att skriva över gruppens databas. Skydda även dumpfiler eftersom de innehåller personuppgifter och hashvärden. En genomförd återläsningskontroll dokumenteras i guidens verifieringsstatus; planerade rutiner ska inte beskrivas som redan automatiserade.

Verifierat 2026-10-12: 18 automatiska tester och 124 riktiga HTTP-kontroller passerade. En dump med syntetiska data återlästes till `blug_forum_restore_test`; tabellernas samtliga radfingeravtryck och en representativ innehålls-/ägarskapsrelation matchade källan. Detta visar en fungerande lokal återläsning, inte att en daglig backuprutin, krypterad backupförvaring eller juridisk efterlevnad har etablerats. Livekontrollen hittade och verifierade också fixen som bevarar 403 vid anonym CSRF-nekning genom servlet ERROR-dispatch.

Nästa sprint bör begränsa loginförsök, lägga till paginering för större datamängder samt granska XSS-säker rendering i frontend. Före extern drift behövs TLS, lämpliga cookie-/databasinställningar, integritetsinformation och fastställt personuppgiftsansvar. Lokala databaslösenord och `ddl-auto: update` är utvecklingsinställningar. Efterlevnad av GDPR är inte fastställd genom dessa kodändringar.
