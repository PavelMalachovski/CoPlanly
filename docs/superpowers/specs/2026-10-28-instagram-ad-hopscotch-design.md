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
