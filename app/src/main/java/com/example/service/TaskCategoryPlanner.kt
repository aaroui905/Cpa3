package com.example.service

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class CategoryDefinition(
    val id: String,
    val labelEn: String,
    val labelAr: String,
    val emoji: String,
    val priority: Int, // Lower number executes earlier in funnel
    val description: String,
    val matchingKeywords: List<String>
)

data class PlannedStep(
    val order: Int,
    val id: String,
    val labelEn: String,
    val labelAr: String,
    val emoji: String,
    val priority: Int,
    val description: String
)

data class ExtractedPlanResult(
    val detectedName: String,
    val targetUrl: String,
    val categories: List<String>,
    val orderedSteps: List<PlannedStep>,
    val summary: String,
    val recommendedMode: String, // "mode1", "mode2", "mode3"
    val recommendedDuration: Int,
    val recommendedKeywords: String,
    val recommendedReferer: String,
    val explanationAr: String,
    val explanationEn: String
)

data class PresetOfferPlan(
    val id: String,
    val name: String,
    val url: String,
    val categories: String,
    val mode: String,
    val duration: Int,
    val completionKeywords: String,
    val descriptionAr: String,
    val descriptionEn: String
)

data class PageWorkStep(
    val target: String,
    val action: String,
    val reason: String,
    val confidence: Int = 70
)

data class AdaptivePagePlan(
    val pageType: String,
    val pageTypeAr: String,
    val recommendedCategory: String,
    val recommendedCategoryAr: String,
    val summary: String,
    val pageActions: List<PageWorkStep>,
    val workMap: List<PageWorkStep>,
    val fallbackStrategy: String,
    val confidence: Int
)

object TaskCategoryPlanner {

    val PRESET_CATEGORIES = listOf(
        CategoryDefinition(
            id = "offer_click",
            labelEn = "Offer Click",
            labelAr = "النقرة على العرض",
            emoji = "🖱️",
            priority = 1,
            description = "Scan landing page for specified target text (e.g., 'Get $1000 Walmart gift card'), auto-click to transfer to the main destination offer site",
            matchingKeywords = listOf("offer click", "click offer", "النقرة على العرض", "النقرة", "نقرة", "click", "نقر", "offer_click", "عرض")
        ),
        CategoryDefinition(
            id = "page_scan",
            labelEn = "Page Scan",
            labelAr = "فحص الصفحة",
            emoji = "🔎",
            priority = 2,
            description = "Inspect layout, buttons, fields, text labels and dominant call-to-action before acting",
            matchingKeywords = listOf("page scan", "scan page", "layout", "inspect", "فحص", "مراجعة الصفحة")
        ),
        CategoryDefinition(
            id = "scroll_action",
            labelEn = "Scroll Action",
            labelAr = "التمرير",
            emoji = "📜",
            priority = 6,
            description = "Scroll until the intended form or CTA becomes visible on the page",
            matchingKeywords = listOf("scroll", "scroll action", "scroll down", "تمرير", "اسكرول")
        ),
        CategoryDefinition(
            id = "email_submit",
            labelEn = "Email Submit",
            labelAr = "إدخال البريد",
            emoji = "✉️",
            priority = 10,
            description = "Detect single email input field on landing page, enter email, and proceed",
            matchingKeywords = listOf("email", "email submit", "mail", "بريد", "ايميل")
        ),
        CategoryDefinition(
            id = "zip_submit",
            labelEn = "Zip Submit",
            labelAr = "رمز بريدي",
            emoji = "📍",
            priority = 20,
            description = "Detect postal/zip code input for geo-targeting and submit",
            matchingKeywords = listOf("zip", "postal", "zip submit", "رمز بريدي", "كود بريدي")
        ),
        CategoryDefinition(
            id = "lead_gen",
            labelEn = "Lead Gen Form",
            labelAr = "استمارة بيانات",
            emoji = "📋",
            priority = 30,
            description = "Fill complete contact info (first & last name, address, phone, city, state)",
            matchingKeywords = listOf("lead", "lead gen", "form", "contact", "بيانات", "استمارة")
        ),
        CategoryDefinition(
            id = "survey_quiz",
            labelEn = "Survey / Quiz",
            labelAr = "استبيان وأسئلة",
            emoji = "📝",
            priority = 40,
            description = "Answer qualification questions, select interactive button answers and radios",
            matchingKeywords = listOf("survey", "quiz", "questions", "poll", "استبيان", "اسئلة", "كويز")
        ),
        CategoryDefinition(
            id = "skip_upsells",
            labelEn = "Skip Upsells",
            labelAr = "تخطي العروض",
            emoji = "⏭️",
            priority = 50,
            description = "Identify sponsored co-reg offers and click 'No Thanks', 'Skip', 'Not interested'",
            matchingKeywords = listOf("skip", "upsell", "no thanks", "sponsor", "تخطي", "عروض اضافية")
        ),
        CategoryDefinition(
            id = "sign_up",
            labelEn = "Sign Up",
            labelAr = "تسجيل حساب",
            emoji = "👤",
            priority = 60,
            description = "Fill account registration fields, generate secure password, and submit register",
            matchingKeywords = listOf("sign up", "signup", "register", "create account", "تسجيل", "انشاء حساب")
        ),
        CategoryDefinition(
            id = "pin_submit",
            labelEn = "PIN Submit",
            labelAr = "تأكيد الهاتف",
            emoji = "📱",
            priority = 70,
            description = "Enter mobile phone number and prepare for carrier verification / SMS",
            matchingKeywords = listOf("pin", "pin submit", "sms", "phone verify", "تاكيد هاتف", "رقم الهاتف")
        ),
        CategoryDefinition(
            id = "terms_agreement",
            labelEn = "Terms Agreement",
            labelAr = "موافقة الشروط",
            emoji = "📜",
            priority = 80,
            description = "Ensure terms, privacy policy, and 18+ majority age checkboxes are checked",
            matchingKeywords = listOf("terms", "agree", "checkbox", "18", "شروط", "موافقة")
        ),
        CategoryDefinition(
            id = "captcha_solver",
            labelEn = "Captcha / Verification",
            labelAr = "كابتشا / تحقق",
            emoji = "🛡️",
            priority = 85,
            description = "Handle captcha or verification challenge before final submission",
            matchingKeywords = listOf("captcha", "verify", "verification", "challenge", "robot", "security", "كابتشا", "تحقق")
        ),
        CategoryDefinition(
            id = "address_capture",
            labelEn = "Address / Shipping",
            labelAr = "العنوان والشحن",
            emoji = "🚚",
            priority = 33,
            description = "Fill shipping, address, city, zip and delivery details when required",
            matchingKeywords = listOf("address", "shipping", "delivery", "city", "zip", "street", "عنوان", "شحن")
        ),
        CategoryDefinition(
            id = "cta_click",
            labelEn = "CTA / Continue",
            labelAr = "زر المتابعة",
            emoji = "➡️",
            priority = 95,
            description = "Press the main call to action button such as Continue, Next, Submit, Claim, Start",
            matchingKeywords = listOf("continue", "next", "submit", "claim", "start", "continue button", "متابعة", "التالي", "إرسال")
        ),
        CategoryDefinition(
            id = "sweepstakes",
            labelEn = "Sweepstakes Claim",
            labelAr = "مسابقات وجوائز",
            emoji = "🎁",
            priority = 15,
            description = "Sweepstakes entry, gift card claim verification and prize draw opt-in",
            matchingKeywords = listOf("sweepstakes", "sweep", "prize", "gift card", "reward", "win", "draw", "مسابقة", "سحب", "جائزة", "بطاقة هدية")
        ),
        CategoryDefinition(
            id = "app_install",
            labelEn = "App Install",
            labelAr = "تثبيت التطبيقات",
            emoji = "📲",
            priority = 35,
            description = "Detect app store / APK download redirect buttons and trigger install flow",
            matchingKeywords = listOf("install", "download", "app", "play store", "apk", "تثبيت", "تنزيل", "تطبيق")
        ),
        CategoryDefinition(
            id = "financial_quote",
            labelEn = "Financial & Insurance",
            labelAr = "التأمين والقروض",
            emoji = "💼",
            priority = 48,
            description = "Multi-step quote funnels (auto insurance, life, personal loans, solar energy)",
            matchingKeywords = listOf("insurance", "quote", "loan", "mortgage", "solar", "credit", "قرض", "تأمين", "عرض سعر")
        ),
        CategoryDefinition(
            id = "gaming_reward",
            labelEn = "Gaming & Reward",
            labelAr = "ألعاب ومكافآت",
            emoji = "🎮",
            priority = 45,
            description = "Gaming reward portals, unlock coins/tokens, level completion and play bonuses",
            matchingKeywords = listOf("game", "gaming", "play", "coins", "points", "reward zone", "لعبة", "العاب", "نقاط")
        ),
        CategoryDefinition(
            id = "ecommerce_trial",
            labelEn = "Free Trial & Shipping",
            labelAr = "تجربة مجانية وشحن",
            emoji = "📦",
            priority = 75,
            description = "Sample trial checkout (shipping and handling address entry)",
            matchingKeywords = listOf("trial", "sample", "free sample", "shipping", "handling", "تجربة", "عينة")
        ),
        CategoryDefinition(
            id = "completion_confirm",
            labelEn = "Confirmation",
            labelAr = "تأكيد الإكمال",
            emoji = "🏆",
            priority = 90,
            description = "Confirm reward claim, detect thank-you/success receipt and finalize conversion",
            matchingKeywords = listOf("confirm", "claim", "thank you", "complete", "تاكيد", "مكافأة")
        )
    )

    val PRESET_OFFER_PLANS = listOf(
        PresetOfferPlan(
            id = "plan_gdfqo_blogspot",
            name = "Gdfqo Blogspot Landing Page (User CPA Offer)",
            url = "https://gdfqo.blogspot.com",
            categories = "Offer Click, Email Submit, Terms Agreement, Survey / Quiz, Lead Gen Form, Skip Upsells, Confirmation",
            mode = "mode1",
            duration = 50,
            completionKeywords = "thank you, congratulations, success, confirmed, completed, verified, reward",
            descriptionAr = "خطة موقعك (gdfqo.blogspot.com): تبدأ دائماً بـ 'النقرة على العرض' رقم 1 للنقر على نص العرض تلقائياً والتحول لموقع العرض الهدف، ثم إدخال البريد والموافقة على الشروط وتخطي العروض الترويجية.",
            descriptionEn = "Your Blogspot funnel: Prioritizes 'Offer Click' at #1 to automatically scan and click the offer text (e.g. 'Get $1000 Walmart gift card'), transferring to the main target offer site before completing the CPA form."
        ),
        PresetOfferPlan(
            id = "plan_ctc_100gc",
            name = "ConsumerTestConnect ($100 GC)",
            url = "https://consumertestconnect.com/ctc-100gcsweep",
            categories = "Email Submit, Terms Agreement, Survey / Quiz, Lead Gen Form, Skip Upsells, Confirmation",
            mode = "mode1",
            duration = 45,
            completionKeywords = "thank you, congratulations, success, confirmed, sweepstakes, completed",
            descriptionAr = "خطة متكاملة تشمل البريد، الموافقة على الشروط وتأكيد السن (18+)، استبيان التأهيل، ملء الاستمارة، وتخطي العروض الدعائية تلقائياً للوصول للتحويل.",
            descriptionEn = "Complete funnel: Email opt-in, 18+ terms agreement, qualification survey, lead gen contact info, upsell wall bypass, and claim confirmation."
        ),
        PresetOfferPlan(
            id = "plan_amazon_500",
            name = "Amazon $500 Card Survey",
            url = "https://nationalconsumercenter.com/amazon-500-sweep",
            categories = "Email Submit, Survey / Quiz, Terms Agreement, Lead Gen Form, Skip Upsells, Confirmation",
            mode = "mode3",
            duration = 50,
            completionKeywords = "thank you, congratulations, success, claim, verified, reward",
            descriptionAr = "خطة قسيمة أمازون: إدخال البريد، أسئلة التسوق المفضلة، تعبئة بيانات الشحن وتأكيد المطالبة الذكي.",
            descriptionEn = "Amazon Gift Card: Email submission, shopping preference quiz, address demographic submission, and keyword verification."
        ),
        PresetOfferPlan(
            id = "plan_cashapp_750",
            name = "Cash App $750 Fast Reward",
            url = "https://rewardzoneusa.com/cash-app-750-reward",
            categories = "Email Submit, Terms Agreement, Survey / Quiz, Skip Upsells, Confirmation",
            mode = "mode1",
            duration = 40,
            completionKeywords = "thank you, completed, reward credited, congratulations, transfer",
            descriptionAr = "مسار Cash App السريع: تسجيل البريد، تخطي عروض الشركات الراعية، وإتمام التأكيد التلقائي.",
            descriptionEn = "Cash App reward path: Email submission, terms consent, sponsor deal bypass, and instant completion verification."
        ),
        PresetOfferPlan(
            id = "plan_sweep_win",
            name = "US Sweepstakes Prize Draw ($1,500)",
            url = "https://instant-rewards.us/sweep-entry",
            categories = "Sweepstakes Claim, Email Submit, Zip Submit, Survey / Quiz, Skip Upsells, Confirmation",
            mode = "mode1",
            duration = 45,
            completionKeywords = "entry confirmed, congratulations, official rules, ticket number",
            descriptionAr = "مسار مسابقات الجوائز: فحص الرمز البريدي، تسجيل البريد، استبيان المستهلكين، وتأكيد رقم تذكرة السحب.",
            descriptionEn = "Sweepstakes Funnel: Postal code entry, email registration, demographic quiz, and draw ticket verification."
        ),
        PresetOfferPlan(
            id = "plan_quote_lead",
            name = "Homeowners Solar & Insurance Quote",
            url = "https://quote-generator.us/solar-quote-lead",
            categories = "Zip Submit, Financial & Insurance, Lead Gen Form, Survey / Quiz, Terms Agreement, Confirmation",
            mode = "mode1",
            duration = 55,
            completionKeywords = "quote ready, thank you, representative, estimate, confirmed",
            descriptionAr = "مسار عروض التأمين والطاقة: فحص الرمز البريدي أولاً، ملء استمارة بيانات العقار، وتأكيد طلب التقدير.",
            descriptionEn = "Quote funnel: ZIP targeting, property details questionnaire, contact info lead generation, and quote confirmation."
        )
    )

    data class PageAnalysisReport(
        val url: String = "",
        val title: String = "",
        val detectedCategory: String = "general",
        val detectedCategoryAr: String = "عام",
        val confidence: Int = 50,
        val summary: String = "",
        val fieldsCount: Int = 0,
        val emailFields: Int = 0,
        val textFields: Int = 0,
        val passwordFields: Int = 0,
        val checkboxesCount: Int = 0,
        val radioGroupsCount: Int = 0,
        val selectCount: Int = 0,
        val buttonsCount: Int = 0,
        val hasSkipButtons: Boolean = false,
        val hasOfferClickCandidate: Boolean = false,
        val isConfirmationPage: Boolean = false,
        val recommendedNextAction: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    fun parseAnalysisReport(rawJson: String): PageAnalysisReport {
        return try {
            val obj = JSONObject(rawJson)
            val cat = obj.optString("detectedCategory", "general")
            val catDef = findDefinition(cat)
            PageAnalysisReport(
                url = obj.optString("url", ""),
                title = obj.optString("title", ""),
                detectedCategory = catDef?.labelEn ?: cat,
                detectedCategoryAr = catDef?.labelAr ?: cat,
                confidence = obj.optInt("confidence", 70),
                summary = obj.optString("summary", "Page analyzed"),
                fieldsCount = obj.optInt("fieldsCount", 0),
                emailFields = obj.optInt("emailFields", 0),
                textFields = obj.optInt("textFields", 0),
                passwordFields = obj.optInt("passwordFields", 0),
                checkboxesCount = obj.optInt("checkboxesCount", 0),
                radioGroupsCount = obj.optInt("radioGroupsCount", 0),
                selectCount = obj.optInt("selectCount", 0),
                buttonsCount = obj.optInt("buttonsCount", 0),
                hasSkipButtons = obj.optBoolean("hasSkipButtons", false),
                hasOfferClickCandidate = obj.optBoolean("hasOfferClickCandidate", false),
                isConfirmationPage = obj.optBoolean("isConfirmationPage", false),
                recommendedNextAction = obj.optString("recommendedNextAction", "Continue automation flow"),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            PageAnalysisReport(summary = "Analysis parsed with fallback")
        }
    }

    fun parseCategories(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun findDefinition(categoryName: String): CategoryDefinition? {
        val normalized = categoryName.trim().lowercase()
        return PRESET_CATEGORIES.firstOrNull { def ->
            def.id == normalized ||
            def.labelEn.lowercase() == normalized ||
            def.labelAr.lowercase() == normalized ||
            def.matchingKeywords.any { kw -> normalized.contains(kw) }
        }
    }

    /**
     * Intelligently arranges and orders the user-supplied categories into
     * an optimal CPA conversion funnel execution pipeline.
     */
    fun orderCategories(categories: List<String>): List<PlannedStep> {
        if (categories.isEmpty()) return emptyList()

        // Match each user category to known definitions or assign dynamic priority
        val matchedList = categories.map { userCat ->
            val def = findDefinition(userCat)
            if (def != null) {
                PlannedStep(
                    order = 0,
                    id = def.id,
                    labelEn = def.labelEn,
                    labelAr = def.labelAr,
                    emoji = def.emoji,
                    priority = def.priority,
                    description = def.description
                )
            } else {
                // Custom user category
                val inferredPriority = when {
                    userCat.contains("click", ignoreCase = true) || userCat.contains("نقرة", ignoreCase = true) || userCat.contains("نقر", ignoreCase = true) -> 1
                    userCat.contains("email", ignoreCase = true) || userCat.contains("mail", ignoreCase = true) -> 12
                    userCat.contains("zip", ignoreCase = true) || userCat.contains("postal", ignoreCase = true) -> 22
                    userCat.contains("form", ignoreCase = true) || userCat.contains("info", ignoreCase = true) -> 32
                    userCat.contains("survey", ignoreCase = true) || userCat.contains("quiz", ignoreCase = true) -> 42
                    userCat.contains("skip", ignoreCase = true) || userCat.contains("pass", ignoreCase = true) -> 52
                    userCat.contains("sign", ignoreCase = true) || userCat.contains("reg", ignoreCase = true) -> 62
                    userCat.contains("pin", ignoreCase = true) || userCat.contains("sms", ignoreCase = true) -> 72
                    userCat.contains("term", ignoreCase = true) || userCat.contains("agree", ignoreCase = true) -> 82
                    else -> 55 // Default mid-priority
                }
                PlannedStep(
                    order = 0,
                    id = userCat.lowercase().replace(" ", "_"),
                    labelEn = userCat,
                    labelAr = userCat,
                    emoji = "🎯",
                    priority = inferredPriority,
                    description = "Custom automated step for '$userCat'"
                )
            }
        }

        // Sort by funnel priority and re-index
        return matchedList
            .distinctBy { it.id }
            .sortedBy { it.priority }
            .mapIndexed { index, step ->
                step.copy(order = index + 1)
            }
    }

    /**
     * Builds a human-readable summary of the AI execution plan.
     * E.g.: "1. ✉️ Email Submit ➔ 2. 📝 Survey / Quiz ➔ 3. 👤 Sign Up"
     */
    fun formatPlanSummary(categories: List<String>): String {
        val steps = orderCategories(categories)
        if (steps.isEmpty()) return "Standard Full-Auto Flow"
        return steps.joinToString(" ➔ ") { "${it.order}. ${it.emoji} ${it.labelEn}" }
    }

    fun buildAdaptivePagePlan(
        url: String,
        categories: List<String> = emptyList(),
        contextText: String = ""
    ): AdaptivePagePlan {
        val normalizedUrl = (url ?: "").trim().lowercase()
        val normalizedText = (contextText ?: "").lowercase()
        val mergedText = "$normalizedUrl $normalizedText"

        val inferredCategory = when {
            mergedText.contains("survey") || mergedText.contains("quiz") || mergedText.contains("opinion") -> "Survey / Quiz"
            mergedText.contains("signup") || mergedText.contains("register") || mergedText.contains("create account") -> "Sign Up"
            mergedText.contains("offer") || mergedText.contains("gift") || mergedText.contains("reward") || mergedText.contains("sweep") -> "Offer Click"
            mergedText.contains("zip") || mergedText.contains("postal") || mergedText.contains("address") -> "Zip Submit"
            mergedText.contains("lead") || mergedText.contains("quote") || mergedText.contains("insurance") -> "Lead Gen Form"
            mergedText.contains("terms") || mergedText.contains("agree") || mergedText.contains("privacy") -> "Terms Agreement"
            mergedText.contains("captcha") || mergedText.contains("verify") -> "Captcha / Verification"
            mergedText.contains("thank") || mergedText.contains("success") || mergedText.contains("completed") -> "Confirmation"
            else -> if (categories.isNotEmpty()) categories.first() else "Page Scan"
        }

        val categoryList = if (categories.isNotEmpty()) categories else listOf(
            "Offer Click",
            "Email Submit",
            "Survey / Quiz",
            "Lead Gen Form",
            "Terms Agreement",
            "Confirmation"
        )

        val pageType = when {
            mergedText.contains("survey") || mergedText.contains("quiz") -> "survey"
            mergedText.contains("signup") || mergedText.contains("register") -> "registration"
            mergedText.contains("gift") || mergedText.contains("reward") || mergedText.contains("sweep") -> "reward_offer"
            mergedText.contains("quote") || mergedText.contains("insurance") || mergedText.contains("loan") -> "lead_gen"
            mergedText.contains("thank") || mergedText.contains("success") || mergedText.contains("confirmation") -> "confirmation"
            else -> "general"
        }

        val pageTypeAr = when (pageType) {
            "survey" -> "استبيان"
            "registration" -> "تسجيل"
            "reward_offer" -> "عرض مكافأة"
            "lead_gen" -> "استمارة بيانات"
            "confirmation" -> "تأكيد الإكمال"
            else -> "صفحة عامة"
        }

        val workMap = when (pageType) {
            "survey" -> listOf(
                PageWorkStep("Survey question block", "Read visible labels and answer the most relevant option", "Questions are the crucial branch point on this page", 86),
                PageWorkStep("Email field", "Fill the first email/contact field with a fresh generated address", "Most survey funnels require email before reward verification", 90),
                PageWorkStep("Submit/next button", "Click the strongest CTA matching Continue, Submit, Next, or Claim", "This moves the flow toward completion and confirmation", 88),
                PageWorkStep("Success page", "Wait for the thank-you / confirmation message and record completion", "Final confirmation is the completion signal", 92)
            )
            "registration" -> listOf(
                PageWorkStep("Register form", "Find the primary sign-up field set and fill it in order", "Registration pages usually expose first email/name fields early", 84),
                PageWorkStep("Password / confirm", "Generate a secure password and confirm it if required", "This is a routine field gate before submission", 83),
                PageWorkStep("Terms checkbox", "Check terms/privacy agreement if visible", "Consent is common before final registration", 81),
                PageWorkStep("Create account", "Click the main account creation button", "Completes the registration flow", 92)
            )
            "reward_offer" -> listOf(
                PageWorkStep("Offer button / CTA", "Look for the dominant reward button and click it once", "This is the main bridge toward the offer destination", 95),
                PageWorkStep("Email input", "Use the first suitable email field for the generated identity", "Email collection is the standard first conversion gate", 90),
                PageWorkStep("Scroll to form", "Scroll or expand hidden form sections if the page is long", "Long CPA pages often hide fields below the fold", 82),
                PageWorkStep("Skip / close wall", "Dismiss sponsor or upsell popup by clicking skip/no thanks when present", "Upsell overlays often block conversion", 87)
            )
            "lead_gen" -> listOf(
                PageWorkStep("Zip / postal field", "Fill country-specific zip code if the page asks for it", "Geo targeting is often the first lead field", 85),
                PageWorkStep("Lead form", "Complete contact information and address fields", "This is the main conversion data collection step", 91),
                PageWorkStep("Terms agreement", "Check age, terms, privacy checkboxes when required", "The final lead form often needs consent", 86),
                PageWorkStep("Quote / continue", "Submit the lead form and wait for the confirmation screen", "This moves the funnel to the final verification step", 88)
            )
            "confirmation" -> listOf(
                PageWorkStep("Thank-you state", "Detect final confirmation text or success banner", "This proves the task was successfully completed", 98),
                PageWorkStep("Reward claim", "Record reward/claim keywords if shown", "Successful conversion message is the main trust signal", 96),
                PageWorkStep("Stop automation", "Finalize run and move to the next repeat cycle if enabled", "A successful page ends the active conversion sequence", 100)
            )
            else -> listOf(
                PageWorkStep("Main form or CTA", "Read the page title and visible labels, then target the biggest action button or field block", "Unknown pages still expose a dominant action target", 72),
                PageWorkStep("Field scan", "Detect the first email, zip, text, or select field closest to the primary label", "Smart fallback is based on visible labels and proximity", 73),
                PageWorkStep("Continue button", "Press the nearest submit/continue button after field completion", "This covers pages that do not follow a strict preset funnel", 74),
                PageWorkStep("Success check", "Check for confirmation text, a thank-you banner, or a reward claim message", "This closes the unknown-page fallback loop", 79)
            )
        }

        val generatedActions = categoryList.mapIndexed { index, category ->
            val categoryDef = findDefinition(category)
            PageWorkStep(
                target = categoryDef?.labelEn ?: category,
                action = when {
                    category.contains("Email", ignoreCase = true) -> "Locate and fill email field, then continue"
                    category.contains("Survey", ignoreCase = true) || category.contains("Quiz", ignoreCase = true) -> "Answer the visible questions in the most relevant order"
                    category.contains("Zip", ignoreCase = true) || category.contains("Postal", ignoreCase = true) -> "Enter a valid postal/zip code for the target country"
                    category.contains("Lead", ignoreCase = true) || category.contains("Form", ignoreCase = true) -> "Fill and submit the contact/data form"
                    category.contains("Terms", ignoreCase = true) -> "Check privacy and consent boxes as required"
                    category.contains("Offer", ignoreCase = true) -> "Click the primary offer/CTA to continue toward the destination"
                    category.contains("Confirmation", ignoreCase = true) -> "Wait for thank-you or success confirmation"
                    else -> "Inspect the matching page section and execute the key control action"
                },
                reason = "Matched category ${categoryDef?.labelEn ?: category} to the current page intent; this keeps the task adaptive instead of rigidly following a fixed sequence",
                confidence = 70 + index * 5
            )
        }

        val recommendedCategory = if (categoryList.isNotEmpty()) categoryList.first() else inferredCategory
        val recommendedCategoryDef = findDefinition(recommendedCategory)
        val fallbackStrategy = "Analyze the current page for visible labels, nearby fields, dominant CTA buttons, and confirmation text; when a category is unclear, extract the nearest matching field/button from the page body and build the next action around it instead of relying on a fixed category order."

        return AdaptivePagePlan(
            pageType = pageType,
            pageTypeAr = pageTypeAr,
            recommendedCategory = recommendedCategory,
            recommendedCategoryAr = recommendedCategoryDef?.labelAr ?: recommendedCategory,
            summary = "AI Smart Work Template: ${pageTypeAr} • priority=${recommendedCategory} • ${workMap.firstOrNull()?.action ?: "inspect page and complete the main CTA"}",
            pageActions = generatedActions,
            workMap = workMap,
            fallbackStrategy = fallbackStrategy,
            confidence = minOf(99, 60 + workMap.size * 7 + (if (categories.isNotEmpty()) 10 else 0))
        )
    }

    /**
     * Builds a JSON string representing the ordered plan for JavaScript automation injection.
     */
    fun buildPlanJson(categories: List<String>): String {
        val steps = orderCategories(categories)
        val jsonArray = JSONArray()
        for (s in steps) {
            val obj = JSONObject()
            obj.put("order", s.order)
            obj.put("id", s.id)
            obj.put("label", s.labelEn)
            obj.put("priority", s.priority)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    /**
     * Extracts and optimizes the CPA conversion funnel plan from ANY URL or offer link.
     * Performs comprehensive domain, path, query and archetype analysis.
     */
    fun extractFunnelPlanFromUrl(rawUrl: String): ExtractedPlanResult {
        var cleanUrl = rawUrl.trim()
        if (cleanUrl.isNotBlank() && !cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }

        var host = ""
        var path = ""
        var query = ""
        try {
            val uri = URI.create(cleanUrl)
            host = (uri.host ?: "").lowercase()
            path = (uri.path ?: "").lowercase()
            query = (uri.query ?: "").lowercase()
        } catch (e: Exception) {
            host = cleanUrl.lowercase()
        }

        val fullText = "$cleanUrl $host $path $query".lowercase()

        // 0. User Landing Page / Blogspot / Bridge archetype (Starts with Offer Click as Priority #1)
        if (fullText.contains("gdfqo") || fullText.contains("blogspot") || fullText.contains("landing") || fullText.contains("bridge")) {
            val cats = listOf("Offer Click", "Email Submit", "Terms Agreement", "Survey / Quiz", "Lead Gen Form", "Skip Upsells", "Confirmation")
            val ordered = orderCategories(cats)
            return ExtractedPlanResult(
                detectedName = if (fullText.contains("gdfqo")) "Gdfqo Blogspot CPA Funnel" else "Landing Bridge Offer Click Funnel",
                targetUrl = cleanUrl,
                categories = cats,
                orderedSteps = ordered,
                summary = formatPlanSummary(cats),
                recommendedMode = "mode1",
                recommendedDuration = 50,
                recommendedKeywords = "thank you, congratulations, success, confirmed, completed, verified, reward, claim",
                recommendedReferer = "https://www.google.com",
                explanationAr = "تم الكشف عن صفحة هبوط / تحويل (Blogspot): تبدأ الخطة بـ 'النقرة على العرض' رقم 1 للنقر تلقائياً على نص العرض الهدف (مثل Get \$1000 Walmart gift card) للتحول إلى موقع العرض الرئيسي، ثم تعبئة البريد والموافقة على الشروط وتخطي العروض الدعائية.",
                explanationEn = "Landing Page / Bridge Funnel detected: Starts with 'Offer Click' as Priority #1 to automatically click the designated offer text and redirect to the target offer site, followed by email submit, qualification questions, and confirmation."
            )
        }

        // 1. ConsumerTestConnect ($100 GC) archetype check
        if (fullText.contains("consumertestconnect") || (fullText.contains("ctc") && fullText.contains("100gc"))) {
            val cats = listOf("Email Submit", "Terms Agreement", "Survey / Quiz", "Lead Gen Form", "Skip Upsells", "Confirmation")
            val ordered = orderCategories(cats)
            return ExtractedPlanResult(
                detectedName = "ConsumerTestConnect ($100 GC)",
                targetUrl = cleanUrl,
                categories = cats,
                orderedSteps = ordered,
                summary = formatPlanSummary(cats),
                recommendedMode = "mode1",
                recommendedDuration = 45,
                recommendedKeywords = "thank you, congratulations, success, confirmed, sweepstakes entry, verified, completed",
                recommendedReferer = "https://www.google.com",
                explanationAr = "تم استخراج خطة عرض ConsumerTestConnect بنجاح: مسار تحويل كامل يبدأ بتسجيل البريد الإلكتروني، تأكيد الموافقة على الشروط وبلوغ سن 18 عاماً، الإجابة الذكية على أسئلة الاستبيان التأهيلية، ملء استمارة البيانات الديموغرافية، التخطي التلقائي لعروض الرعاة (Skip Upsells)، والوصول لصفحة تأكيد المكافأة.",
                explanationEn = "Successfully extracted ConsumerTestConnect ($100 GC) plan: Full funnel with Email Submit, Terms & 18+ Age agreement, Qualification Survey, Demographic Lead Gen, Sponsor Deal Skip, and Confirmation claim."
            )
        }

        // 2. Gift Card / Reward / Sweepstakes archetype
        if (fullText.contains("sweep") || fullText.contains("giftcard") || fullText.contains("reward") || fullText.contains("voucher") || fullText.contains("100gc") || fullText.contains("500") || fullText.contains("750") || fullText.contains("1000")) {
            val brand = when {
                fullText.contains("amazon") -> "Amazon $500"
                fullText.contains("walmart") -> "Walmart $100"
                fullText.contains("target") -> "Target $100"
                fullText.contains("cash") || fullText.contains("cashapp") -> "Cash App $750"
                fullText.contains("apple") -> "Apple $500"
                else -> "Rewards Card"
            }
            val cats = listOf("Email Submit", "Terms Agreement", "Survey / Quiz", "Lead Gen Form", "Skip Upsells", "Confirmation")
            val ordered = orderCategories(cats)
            return ExtractedPlanResult(
                detectedName = "$brand Sweepstakes Funnel",
                targetUrl = cleanUrl,
                categories = cats,
                orderedSteps = ordered,
                summary = formatPlanSummary(cats),
                recommendedMode = "mode1",
                recommendedDuration = 45,
                recommendedKeywords = "thank you, congratulations, success, confirmed, claim reward, entry submitted",
                recommendedReferer = "https://www.google.com",
                explanationAr = "خطة مسار جوائز وقسائم: إدخال البريد، الموافقة على الشروط وتأكيد السن، استبيان تأهيلي سريع، إدخال عنوان الشحن والبيانات، وتخطي العروض الدعائية للوصول لصفحة الفوز.",
                explanationEn = "Extracted Reward/Sweepstakes funnel: Email opt-in, terms acceptance, interactive quiz, contact information submit, upsell bypass, and final confirmation."
            )
        }

        // 3. Survey / Opinion / Quiz archetype
        if (fullText.contains("survey") || fullText.contains("quiz") || fullText.contains("opinion") || fullText.contains("poll") || fullText.contains("feedback")) {
            val cats = listOf("Survey / Quiz", "Terms Agreement", "Email Submit", "Confirmation")
            val ordered = orderCategories(cats)
            return ExtractedPlanResult(
                detectedName = "Interactive Survey & Quiz",
                targetUrl = cleanUrl,
                categories = cats,
                orderedSteps = ordered,
                summary = formatPlanSummary(cats),
                recommendedMode = "mode3",
                recommendedDuration = 60,
                recommendedKeywords = "survey completed, thank you, congratulations, success, responses recorded, rewards credited",
                recommendedReferer = "https://www.google.com",
                explanationAr = "خطة استبيان وأسئلة: إجابة الأسئلة التأهيلية بأعلى تقييم وبما يطابق الهوية، الموافقة على الشروط، إدخال البريد الإلكتروني، وانتظار رسالة اكتمال الاستبيان.",
                explanationEn = "Extracted Survey Funnel: High-qualifying question answering, terms agreement, email submission, and smart keyword completion."
            )
        }

        // 4. Lead Gen / Quotes / Insurance / Finance archetype
        if (fullText.contains("quote") || fullText.contains("insurance") || fullText.contains("solar") || fullText.contains("loan") || fullText.contains("mortgage") || fullText.contains("finance") || fullText.contains("credit")) {
            val cats = listOf("Zip Submit", "Lead Gen Form", "Survey / Quiz", "Terms Agreement", "Confirmation")
            val ordered = orderCategories(cats)
            return ExtractedPlanResult(
                detectedName = "Quote & Lead Gen Funnel",
                targetUrl = cleanUrl,
                categories = cats,
                orderedSteps = ordered,
                summary = formatPlanSummary(cats),
                recommendedMode = "mode1",
                recommendedDuration = 55,
                recommendedKeywords = "quote ready, estimate, thank you, successfully submitted, verified",
                recommendedReferer = "https://www.bing.com",
                explanationAr = "خطة مسار بيانات وتقدير عروض: استخراج الرمز البريدي أولاً، تعبئة استمارة بيانات الاتصال، الإجابة على أسئلة المتطلبات، والموافقة على الشروط للحصول على العرض.",
                explanationEn = "Extracted Lead Gen funnel: Geo-targeted Zip code entry, full lead information autofill, qualification questions, and terms agreement."
            )
        }

        // 5. Sign Up / Account Registration archetype
        if (fullText.contains("signup") || fullText.contains("sign-up") || fullText.contains("register") || fullText.contains("join") || fullText.contains("create-account")) {
            val cats = listOf("Sign Up", "Email Submit", "Terms Agreement", "Confirmation")
            val ordered = orderCategories(cats)
            return ExtractedPlanResult(
                detectedName = "Account Registration Flow",
                targetUrl = cleanUrl,
                categories = cats,
                orderedSteps = ordered,
                summary = formatPlanSummary(cats),
                recommendedMode = "mode1",
                recommendedDuration = 40,
                recommendedKeywords = "welcome, account created, verification, success, dashboard",
                recommendedReferer = "https://www.google.com",
                explanationAr = "خطة تسجيل حساب: تعبئة البريد الإلكتروني واسم المستخدم، توليد كلمة مرور معقدة وآمنة تلقائياً، والموافقة على الشروط.",
                explanationEn = "Extracted Sign-Up flow: Email and username detection, secure password generation, terms agreement, and welcome confirmation."
            )
        }

        // 6. Mobile PIN / SMS submit archetype
        if (fullText.contains("pin") || fullText.contains("sms") || fullText.contains("mobile") || fullText.contains("carrier") || fullText.contains("phone")) {
            val cats = listOf("PIN Submit", "Terms Agreement", "Confirmation")
            val ordered = orderCategories(cats)
            return ExtractedPlanResult(
                detectedName = "Mobile PIN Submit Flow",
                targetUrl = cleanUrl,
                categories = cats,
                orderedSteps = ordered,
                summary = formatPlanSummary(cats),
                recommendedMode = "mode1",
                recommendedDuration = 35,
                recommendedKeywords = "pin sent, code verified, success, thank you, subscribed",
                recommendedReferer = "https://m.facebook.com",
                explanationAr = "خطة تأكيد الهاتف والـ PIN: إدخال رقم هاتف أمريكي متطابق مع شبكة المشغل والموافقة على الشروط.",
                explanationEn = "Extracted Mobile PIN submit flow: Carrier detection, phone number entry, and verification confirmation."
            )
        }

        // 7. General High-Converting CPA Offer Fallback
        val cleanDomain = host.removePrefix("www.").substringBefore(".")
        val formattedTitle = if (cleanDomain.isNotBlank() && cleanDomain.length > 2) {
            cleanDomain.replaceFirstChar { it.uppercase() } + " CPA Offer"
        } else {
            "CPA Smart Funnel"
        }

        val defaultCats = listOf("Email Submit", "Terms Agreement", "Survey / Quiz", "Lead Gen Form", "Skip Upsells", "Confirmation")
        val defaultOrdered = orderCategories(defaultCats)
        return ExtractedPlanResult(
            detectedName = formattedTitle,
            targetUrl = cleanUrl,
            categories = defaultCats,
            orderedSteps = defaultOrdered,
            summary = formatPlanSummary(defaultCats),
            recommendedMode = "mode1",
            recommendedDuration = 45,
            recommendedKeywords = "thank you, congratulations, success, confirmed, completed, verified",
            recommendedReferer = "https://www.google.com",
            explanationAr = "تم فحص الرابط وتوليد خطة تحويل شاملة ومحسنة: مسار تلقائي يتضمن إدخال البريد، الموافقة على الشروط، الإجابة على أي أسئلة أو اختيارات، تعبئة بيانات الاتصال، وتخطي العروض الدعائية لضمان إتمام الإحالة بنجاح.",
            explanationEn = "Extracted and optimized robust CPA conversion funnel: Universal flow covering email submit, terms consent, interactive question answering, lead generation, and upsell wall bypass."
        )
    }
}

