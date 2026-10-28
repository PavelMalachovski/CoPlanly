# Instagram ad, variant A «Hopscotch» — design

Date: 28 October 2026. Owner: Pavel. Built with the OBSION Motion skill (motion graphics in HTML, rendered
frame by frame). This is the record of what was agreed in conversation, written so a builder could make
the ad without asking again.

## Intent

A 30-second vertical motion ad for Instagram (Reels and Stories) that presents CoPlanly. One thought, the
owner's words: **co-parenting can be easy, and that is good for your shared child.** It is a pilot: it ends
on "Get it on Google Play" although the app is not published yet.

- **Audience:** parents who live apart. **Language:** English. **Tone:** warm, calm, friendly.
- **It reads with the sound off**, and it has sound for those who listen.

## Goals, non-goals, anti-goals

- **Goals.** The child is at the centre. The real app is shown — the calendar with the new day swap flow,
  the Requests inbox, Home, Chat and the Family tab — as one continuous journey, not a feature list.
- **Non-goals.** A published campaign, a voice-over, other languages, a cut for other platforms.
- **Anti-goals** (`docs/LAUNCH-PLAYBOOK.md` §3.5 and CLAUDE.md design item 8). None of these may appear:
  conflict as the hook, "ex" or "divorced", evidence or courts, the words "Mom" and "Dad", a side taken,
  a feature the app does not have, a price, AI, memes.

## Constraints

- 1080 × 1920, 30 fps, H.264 with AAC, about 30 s.
- Instagram's interface covers the top 220 px and the bottom 380 px; 60 px each side stay clear.
- **Real screenshots only**, from the UI tour of `main` with the demo family (Alice, Bob, Emma, Leo, Max),
  English, dark theme. Nothing is mocked up to look like the app; where the ad changes a screen (a day
  recolouring after a swap), it shows what the app itself does.
- Everything else is drawn in code: this environment has no image generator.
- **Parent colours identify the two parents**: pink is Alice, blue is Bob, as in the app. Leo wears
  neither. Neither colour is used for decoration.
- On-screen text: at least 54 px, at most five words on screen at once, sentence case, Onest.

## Approaches

Three whole-film pitches were drawn as grey animatics and shown to the owner:

| Pitch | Idea | For | Against |
| --- | --- | --- | --- |
| **A — Hopscotch** (picked) | The custody calendar is a chalk hopscotch; a child hops through the days, and the camera reveals the hopscotch is the app's month. | The idea people retell; puts the child at the centre; the real app appears at the reveal. | Hardest to draw well. |
| B — One line | One ink line draws a home, the child's week, a second home ("Home. And home."), the phone. | Gentle, strong line of copy. | Abstract; the product appears late. |
| C — One day's journey | One day cell travels through the swap flow. | Clearest about how the app works; easiest. | Closest to an ordinary app ad. |

The owner picked A and changed two things: **a drawn child (Leo) instead of a pair of shoes**, and **all
the main parts of the app** in the film. The line "Home. And home." comes over from B.

## The story

96 bpm: a beat is 0.625 s and a bar 2.5 s, so scene changes land on bar lines. Leo is the one carrier:
he is in every shot, and the split square he lands on in the first second is the square that becomes the
logo at the end.

| Time | Shot | On screen | Copy |
| --- | --- | --- | --- |
| 0.0–2.5 | **Hook.** Dark asphalt, a chalk hopscotch seen from a low three-quarter angle. Leo is already in the air on frame 0 and lands at 0.6 s on a square split pink and blue — the handover day — one foot in each colour. Chalk dust. | Leo, chalk squares with day numbers | Home. And home. |
| 2.5–5.0 | **Reveal.** Leo hops on, one square per beat. The camera cranes up to look straight down and pulls back: the hopscotch is a whole month. | the month in chalk | One calendar. |
| 5.0–7.5 | **Match.** The chalk lands exactly on the app's month grid; the phone's bezel rises around it. Leo now stands on the phone's tab bar, on Calendar. | the real calendar | See whose day it is. |
| 7.5–12.5 | **Swap.** A finger taps "⇄", chooses days (the new marks), taps Next; the "Swap with Bob" sheet shows what each parent gives; Send. A notification arrives, in the app's own words: "Day swap agreed · Your co-parent agreed to swap 3 days". The days take their new colours. Leo jumps for joy. | choosing screen, swap sheet, notification | Plans change? Swap days. / Both agree. |
| 12.5–15.0 | **Home.** Leo hops to the Home tab; Home fades through. | Home | Today, at a glance. |
| 15.0–17.5 | **Requests.** The Requests row opens the inbox. | Requests | Every request in one place. |
| 17.5–20.0 | **Chat.** Leo hops to Chat. | the thread | Chat about the kids. |
| 20.0–22.5 | **Family.** Leo hops to Family. | the Family tab | Costs and kids' details, shared. |
| 22.5–25.0 | **Back out.** The camera pulls out of the phone and tilts back: the phone lies on the asphalt among the chalk. Leo hops off it onto the squares. | asphalt, phone, chalk | Co-parenting can be easy. |
| 25.0–27.5 | **Payoff.** Leo's last hop lands on the big split square, one foot in each colour, arms up. | Leo | Good for your kids. |
| 27.5–30.0 | **End card.** The square's chalk outline becomes the logo tile. | CoPlanly, "The shared calendar for parents who live apart", "Get it on Google Play" | — |

"Good for your kids" is a value line, not a factual claim: no figure or study is cited on screen.

## Leo

Leo is the demo family's younger child, six years old. He is a flat vector drawing in code, a cut-out puppet
on the skill's `rig.js`: head, hair, torso, arms and legs in two parts each, sneakers. Every move is a
function of time. A hop is anticipation (knees bend), take-off (stretch, arms up), the air (knees tucked),
landing (squash, arms out) and settle; hair and arms follow a beat late, and he blinks now and then. He
wears a mustard T-shirt, olive shorts and white sneakers with an orange stripe — no pink, no blue.

He is a paper figure standing on the scene: on the tilted asphalt his feet sit on projected ground points
with a contact shadow, and in the phone shots he stands on the tab bar's icons like stepping stones.

## Sound

Procedural, from the skill's `sfx.js`, driven by the same event list as the picture: a music bed in a major
key at 96 bpm with its drums entering at the reveal; a soft thud and a chalk tick on every landing; chalk
scratches as copy appears; air for the camera moves; UI clicks for taps; a chime when both agree; a
sparkle on the logo. Mastered to −14 LUFS, true peak under −1.5 dBTP. No voices.

## How it is built

The OBSION Motion pipeline: one deterministic HTML page (a paused GSAP timeline, `window.__seek(t)`),
rendered frame by frame by `render.cjs` into MP4, with the audio rendered offline from the event list. The
riskiest shot (hook → reveal → match, 0–7.5 s) is built and checked frame by frame first. Self-check:
`watch.py` (holds, judder, loudness) and a layout check of every text against the safe zones.

## Calls the builder made (owner, please check)

- **Dark theme**: chalk on dark asphalt matches the dark app, and the match at 5 s depends on it.
- **30 fps and about 30 s.** 30 fps is what Instagram plays; 30 s because the owner asked for every part
  of the app. A 15-second cut is possible later.
- **The swap is shown from Alice's side** (she proposes, Bob agrees), because the new choosing screen and
  sheet are the sender's.
- **The store line is a plain pill, not Google's badge artwork**: the badge is for apps on Google Play.
- The copy lines in the table, and Leo's look.

## Left to the builder

Exact timings within a beat, easings, the chalk texture, each shot's camera path and the choice of sound
for each event.

## As built (first cut, 28 October 2026)

The story above held; these are the details the build settled, and the places it departs from the table.

| Time | Shot | Copy |
| --- | --- | --- |
| 0.0–2.5 | Leo in the air over a chalk hopscotch that is November drawn square by square on the asphalt, seen low with the weeks running into the distance. He lands at 0.6 s astride Mon 9, the handover day: one foot in Bob's blue, one in Alice's pink. Chalk dust. | Home. And home. |
| 2.5–4.4 | He hops on, one day per beat (10, 11, 12, 13), while the camera cranes up, turns and pulls back: the hopscotch is a whole month. | One calendar. |
| 4.4–5.0 | His landing on Fri 13 sends a wave through the chalk: each square lifts away and the app's own cell is under it, at the same place and size. The screen and the bezel assemble around the grid. | — |
| 5.0–6.9 | Leo climbs the weeks' colours (7, 1) to the ⇄ button. | See whose day it is. |
| 6.9–10.3 | His landings are the taps: on ⇄ (the choosing screen opens from the button), on Tue 3, Thu 12 and Fri 13 (each day's mark appears as he lands), and, once the copy has left the top of the frame, on Next. | Plans change? Swap days. |
| 10.3–11.3 | The «Swap with Bob» sheet rises behind him. | — |
| 11.3–13.1 | The sheet goes. A notification in the app's words: «Day swap agreed · Your co-parent agreed to swap 3 days». The three days glow in their new colours and Leo cheers on Tue 3. | Both agree. |
| 13.1–14.4 | He drops onto the Home tab; Home fades through. | Today, at a glance. |
| 14.4–17.0 | He jumps onto the Requests row; the inbox slides in. | Every request in one place. |
| 17.0–19.4 | He pushes off the inbox (Back), lands on Chat, jumps onto a bubble. | Chat about the kids. |
| 19.4–22.4 | Down onto Family. | Costs and kids' details, shared. |
| 22.4–25.0 | The camera tilts back: the phone lies on the asphalt, and Leo hops off it along a short chalk path. | Co-parenting can be easy. |
| 25.0–27.4 | He lands astride a big split square, arms up. | Good for your kids. |
| 27.5–30.0 | The camera looks straight down; the square becomes the app's icon and Leo hops onto it. CoPlanly, the tagline, «Get it on Google Play». | — |

Departures from the story, and why:

- **November throughout, not October.** The new swap screens the tour photographed are November's, so the
  chalk month is November too and there is no month change to explain.
- **Leo is not on the tab bar at 5 s.** He climbs to ⇄ instead, so the swap starts where he already is; he
  reaches the tab bar at Home, and from then on the tabs are his stepping stones.
- **No finger anywhere.** Every tap is one of Leo's landings, which keeps the carrier doing the action.
- **No «Send» tap.** The sheet's Send button is below the photographed part of the sheet; the notification
  says what Send led to.
- **One screen is derived, not photographed: the month after the swap.** The tour has no screenshot of an
  accepted swap, so `cal-nov-after.png` is the tour's November with exactly three cells re-coloured the way
  `DayCellFills` draws an accepted swap (one solid fill in the receiving parent's colour, no mark), every
  other pixel the screenshot's. The script that makes it is kept with the ad's source.
- **The chat is the tour's thread as it is**, with its two system cards at the bottom; Leo stands on a
  bubble whose only covered text is its time.
- **The score is procedural** (D major, D–A–Bm–G, kalimba, bass, soft drums, a bell tune), with a
  150 ms gap before the logo's chord, instead of the skill's stock bed.
- **A tab change keeps the navigation bar on screen** and fades only the content through, as the app's
  NavHost does; the bar's selected tab cross-fades.

Self-check of the first cut (`watch.py`, a layout check against Instagram's margins): one continuous
shot, no hold of a second or more, no judder or duplicate frames, −13.6 LUFS integrated, true peak
−2.0 dBTP, all copy inside the safe area.
