package com.coparently.app.presentation.common

/**
 * The SF Symbol the iPhone draws for each shared Material icon (MON-24 phase 3, part D), keyed by
 * the icon's [androidx.compose.ui.graphics.vector.ImageVector.name]: `Icons.Default.X` and
 * `Icons.Filled.X` are `"Filled.X"`, `Icons.Outlined.X` is `"Outlined.X"`, and
 * `Icons.AutoMirrored.Filled.X` is `"AutoMirrored.Filled.X"`.
 *
 * Data only, copied from the owner-approved table
 * `docs/superpowers/plans/2026-11-16-iphone-phase-3d-sf-symbols.json` (132 rows, approved on
 * 16 November 2026). Don't change a choice here: change the approved table first. `SfSymbolTableTest`
 * holds the two to each other and fails when a shared screen uses an icon with no row.
 *
 * Twelve icons have no row on purpose and keep the Material vector on the iPhone too:
 * - `Filled.AccountBalanceWallet`: SF wallet is iOS 18 (wallet.bifold); wallet.pass means an Apple Wallet pass.
 * - `Filled.Balance`: no scales of justice in SF Symbols; scalemass reads as weight.
 * - `Filled.Diversity3`: no three-joined-people symbol; person.3 already stands for Groups.
 * - `Filled.DoneAll`: no double checkmark (read ticks).
 * - `Filled.EventRepeat`: no calendar-with-repeat symbol; repeat alone already means Repeat.
 * - `Filled.Gavel`: no gavel; hammer reads as tools, not law.
 * - `Filled.LinkOff`: no broken-link symbol in SF Symbols.
 * - `Filled.NoFood`: no crossed-out food symbol (allergies).
 * - `Filled.PictureAsPdf`: no PDF-labelled document symbol.
 * - `Filled.SearchOff`: no crossed-out magnifying glass in SF Symbols.
 * - `Filled.Source`: no folder-with-code symbol.
 * - `Filled.VolunteerActivism`: no hand-holding-heart symbol (the thank-you sticker).
 *
 * An icon with no row, or whose symbol the running iOS cannot load, draws its Material vector
 * ([AppIcon]); it never falls back to another symbol.
 */
internal val SF_SYMBOLS: Map<String, String> = mapOf(
    "AutoMirrored.Filled.ArrowBack" to "chevron.backward",
    "AutoMirrored.Filled.ArrowForward" to "arrow.forward",
    "AutoMirrored.Filled.Assignment" to "list.clipboard.fill",
    "AutoMirrored.Filled.Chat" to "bubble.left.fill",
    "AutoMirrored.Filled.KeyboardArrowLeft" to "chevron.backward",
    "AutoMirrored.Filled.KeyboardArrowRight" to "chevron.forward",
    "AutoMirrored.Filled.Logout" to "rectangle.portrait.and.arrow.right",
    "AutoMirrored.Filled.Message" to "text.bubble.fill",
    "AutoMirrored.Filled.OpenInNew" to "arrow.up.forward.square",
    "AutoMirrored.Filled.ReceiptLong" to "receipt",
    "AutoMirrored.Filled.Send" to "paperplane.fill",
    "AutoMirrored.Filled.Undo" to "arrow.uturn.backward",
    "AutoMirrored.Outlined.Chat" to "bubble.left",
    "Filled.AccountCircle" to "person.crop.circle.fill",
    "Filled.Add" to "plus",
    "Filled.AddAPhoto" to "photo.badge.plus",
    "Filled.AddLink" to "link.badge.plus",
    "Filled.AttachFile" to "paperclip",
    "Filled.AutoAwesome" to "sparkles",
    "Filled.Block" to "nosign",
    "Filled.Bloodtype" to "drop.fill",
    "Filled.Bolt" to "bolt.fill",
    "Filled.Book" to "book.fill",
    "Filled.CalendarMonth" to "calendar",
    "Filled.Call" to "phone.fill",
    "Filled.Celebration" to "party.popper.fill",
    "Filled.Check" to "checkmark",
    "Filled.CheckCircle" to "checkmark.circle.fill",
    "Filled.Checkroom" to "hanger",
    "Filled.ChevronLeft" to "chevron.left",
    "Filled.ChevronRight" to "chevron.right",
    "Filled.ChildCare" to "figure.and.child.holdinghands",
    "Filled.Close" to "xmark",
    "Filled.CloudOff" to "icloud.slash.fill",
    "Filled.Contacts" to "person.text.rectangle.fill",
    "Filled.ContentCopy" to "doc.on.doc",
    "Filled.DateRange" to "calendar",
    "Filled.Delete" to "trash.fill",
    "Filled.DeleteForever" to "xmark.bin.fill",
    "Filled.Description" to "doc.text.fill",
    "Filled.DirectionsBus" to "bus.fill",
    "Filled.Edit" to "pencil",
    "Filled.EditNote" to "square.and.pencil",
    "Filled.Email" to "envelope.fill",
    "Filled.Error" to "exclamationmark.circle.fill",
    "Filled.ErrorOutline" to "exclamationmark.circle",
    "Filled.Event" to "calendar",
    "Filled.EventAvailable" to "calendar.badge.checkmark",
    "Filled.EventBusy" to "calendar.badge.minus",
    "Filled.ExpandLess" to "chevron.up",
    "Filled.ExpandMore" to "chevron.down",
    "Filled.FamilyRestroom" to "figure.2.and.child.holdinghands",
    "Filled.Favorite" to "heart.fill",
    "Filled.FileDownload" to "square.and.arrow.down",
    "Filled.FilterList" to "line.3.horizontal.decrease",
    "Filled.FolderShared" to "folder.fill.badge.person.crop",
    "Filled.Group" to "person.2.fill",
    "Filled.Groups" to "person.3.fill",
    "Filled.History" to "clock.arrow.circlepath",
    "Filled.Home" to "house.fill",
    "Filled.HourglassTop" to "hourglass.tophalf.fill",
    "Filled.Image" to "photo.fill",
    "Filled.Info" to "info.circle.fill",
    "Filled.Insights" to "chart.line.uptrend.xyaxis",
    "Filled.Key" to "key.fill",
    "Filled.Language" to "globe",
    "Filled.Link" to "link",
    "Filled.LocationCity" to "building.2.fill",
    "Filled.Lock" to "lock.fill",
    "Filled.MedicalServices" to "cross.case.fill",
    "Filled.Mic" to "mic.fill",
    "Filled.MoreVert" to "ellipsis",
    "Filled.Notifications" to "bell.fill",
    "Filled.NotificationsActive" to "bell.and.waves.left.and.right.fill",
    "Filled.Palette" to "paintpalette.fill",
    "Filled.Payments" to "banknote.fill",
    "Filled.Person" to "person.fill",
    "Filled.PersonAdd" to "person.fill.badge.plus",
    "Filled.PersonOutline" to "person",
    "Filled.PersonRemove" to "person.fill.badge.minus",
    "Filled.Pets" to "pawprint.fill",
    "Filled.Phone" to "phone.fill",
    "Filled.PhotoCamera" to "camera.fill",
    "Filled.PriorityHigh" to "exclamationmark",
    "Filled.PrivacyTip" to "hand.raised.fill",
    "Filled.Public" to "globe.europe.africa.fill",
    "Filled.QrCodeScanner" to "qrcode.viewfinder",
    "Filled.Refresh" to "arrow.clockwise",
    "Filled.Repeat" to "repeat",
    "Filled.Restaurant" to "fork.knife",
    "Filled.Schedule" to "clock.fill",
    "Filled.School" to "graduationcap.fill",
    "Filled.Search" to "magnifyingglass",
    "Filled.Settings" to "gearshape.fill",
    "Filled.Share" to "square.and.arrow.up",
    "Filled.SportsSoccer" to "soccerball",
    "Filled.Star" to "star.fill",
    "Filled.Stop" to "stop.fill",
    "Filled.SwapHoriz" to "arrow.left.arrow.right",
    "Filled.Sync" to "arrow.triangle.2.circlepath",
    "Filled.SyncAlt" to "arrow.left.arrow.right",
    "Filled.TableChart" to "tablecells",
    "Filled.TextFields" to "textformat.size",
    "Filled.ThumbUp" to "hand.thumbsup.fill",
    "Filled.Title" to "textformat",
    "Filled.Today" to "calendar",
    "Filled.Toys" to "teddybear.fill",
    "Filled.Vaccines" to "syringe.fill",
    "Filled.Visibility" to "eye.fill",
    "Filled.VisibilityOff" to "eye.slash.fill",
    "Filled.Warning" to "exclamationmark.triangle.fill",
    "Outlined.CalendarMonth" to "calendar",
    "Outlined.Flag" to "flag",
    "Outlined.Groups" to "person.3",
    "Outlined.Home" to "house",
    "Outlined.Lightbulb" to "lightbulb",
    "Outlined.MicOff" to "mic.slash",
)
