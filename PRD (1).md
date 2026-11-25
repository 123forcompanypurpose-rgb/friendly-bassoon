# PRD — Product Requirements Document

## 1. Vision
A mobile-first web platform where anyone can write serialized stories chapter by chapter and where readers discover, follow, and binge them. Free to read at launch; monetization (coins for premium chapters) added after traction.

## 2. Problem
- Readers: hard to find good serialized fiction, ads/paywalls are heavy on big apps, regional-language content is thin.
- Writers: need a simple way to publish chapter by chapter, get feedback, build an audience, and eventually earn.

## 3. Target users (personas)
| Persona | Needs |
|---|---|
| **Reader (Riya, 22, mobile, India)** | Fast loading, readable chapters on phone, resume where she left off, library, notifications for new chapters, dark mode |
| **Writer (Arjun, 28, hobbyist)** | Clean editor, autosave drafts, schedule chapters, see views/votes/comments, build followers |
| **Global reader (Sam, 30, romance/fantasy)** | Genre browse, rankings, tags, English UI |
| **Moderator/Admin** | Review reports, take down content, ban users, feature stories, view stats |

## 4. Goals & non-goals
**Goals (v1):** read, write, publish, discover, follow, comment, vote, library, notifications, moderation, SEO-friendly, deployable free.
**Non-goals (v1):** native mobile apps, audio books, video, AI-generated stories, real-time chat, microservices, recommendation ML, offline mode.

## 5. Feature list & priority
Legend: **P0** = MVP must-have, **P1** = launch-quality, **P2** = after traction.

### Accounts
- P0 Register/login (email+password), email verification, forgot/reset password
- P0 Google login (OAuth)
- P0 Public profile (avatar, bio, stories, followers count)
- P1 Edit profile, change password, delete account, notification preferences
- P2 Phone OTP login (India), Apple login

### Reading
- P0 Home (featured, trending, new releases, genre rows)
- P0 Story detail (cover, blurb, tags, stats, chapter list, rating, author)
- P0 Chapter reader: clean typography, prev/next, font size, theme (light/sepia/dark), line spacing
- P0 Resume reading (reading progress per story)
- P0 Library: add/remove story, "Continue reading"
- P1 Reading lists (custom collections)
- P1 Chapter-level and paragraph-level comments (paragraph = P2)
- P1 Votes (like) on stories/chapters, star rating + review on stories
- P2 Offline/PWA

### Writing (Writer Studio)
- P0 Create story: title, blurb, cover upload, genre, tags, language, maturity rating, status (ongoing/completed)
- P0 Rich-text chapter editor, autosave draft, publish/unpublish, reorder chapters
- P1 Schedule publish, word count, per-story analytics (views, votes, comments, library adds)
- P1 Co-author (P2)

### Discovery
- P0 Search by title/author/tag
- P0 Browse by genre, tag, status, language, sort (trending/new/top rated/most read)
- P1 Rankings (daily/weekly/all-time), editor's picks
- P2 Personalized recommendations

### Social
- P0 Follow authors, get notified on new chapters
- P1 Comments with replies, comment likes, report comment
- P1 In-app notifications (+ email digest P2)

### Moderation/Admin
- P0 Report story/chapter/comment/user; admin report queue; take down content; ban/suspend user
- P1 Feature stories on home, manage genres/tags, announcements, basic dashboard
- P1 Mature-content flag + 18+ gate
- P1 DMCA takedown form + workflow

### Monetization (Phase after launch)
- P2 Coin wallet, coin packages, buy via Razorpay (India) / Stripe (global)
- P2 Paid (locked) chapters, unlock with coins, author earnings ledger, payout request (manual at first)
- P2 Daily check-in free coins, ads slots (optional)

## 6. User journeys (summary)
1. **Reader:** land on Home → open story → read ch.1 (no login needed) → prompted to sign up to save to library/comment → returns tomorrow, "Continue reading".
2. **Writer:** sign up → "Write" → create story → write chapter → publish → share link → see views/votes → reply to comments.
3. **Moderator:** report notification → open queue → view content → remove/ignore → notify author.

## 7. Success metrics
- Activation: % of new users who read ≥1 chapter in first session (target >60%)
- Retention: D7 reader return (target >20%)
- Supply: # stories with ≥3 chapters; # active writers/week
- Engagement: avg chapters read/session, comments per 100 reads
- Perf: LCP < 2.5s on 4G for chapter pages
- Later: ARPPU, coin conversion %

## 8. Monetization strategy (later)
Free reading drives SEO + growth. Premium chapters unlocked with coins; writers get a revenue share (e.g., 50–70%, configurable). Payments: Razorpay (INR, UPI) and Stripe (USD). Coins are non-refundable except as required by law. Requires T&Cs, refund policy, KYC/tax info for payouts.

## 9. Assumptions / risks
| Risk | Mitigation |
|---|---|
| Copyright infringement (users upload others' novels) | DMCA flow, report button, repeat-infringer policy, only original works allowed in ToS |
| Adult/illegal content | Maturity ratings, 18+ gate, report queue, hard-ban list (minors sexual content, etc.) |
| Spam/bots | Email verification, rate limits, captcha on signup, shadow limits |
| Cold start of free hosting | Keep-alive/health pings, caching, SSR/ISR pages cached at the edge |
| Free-tier limits | Document limits, monitoring, upgrade path in `DEPLOYMENT.md` |
| Cold-start content problem | Seed with your own original stories, invite writers first |

## 10. Release plan
- **Alpha (internal):** Phases 1–6 of playbook
- **Beta (invite writers):** + search, notifications, moderation, SEO
- **Public v1:** + hardening, deploy, legal pages
- **v1.5:** coins + paid chapters
