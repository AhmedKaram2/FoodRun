package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

/** Structured bilingual menu authoring, shared by personal and administrator catalogs. */
internal class GroupMenuEditor(private val c: GroupController) {
    private var type = "item"
    private var id = ""
    private var parent = ""
    private var groups = emptyList<String>()
    private val menu get() = requireNotNull(c.editingRestaurant).restaurant.menu
    private fun tr(en: String, ar: String) = if(c.library.language == "ar") ar else en
    private fun set(menu: Menu) { val export = requireNotNull(c.editingRestaurant); c.editingRestaurant = export.copy(restaurant = export.restaurant.copy(menu = menu)) }
    private fun field(key: GroupFieldKey, en: String, ar: String, toggle: Boolean = false, multiline: Boolean = false, choices: List<GroupChoice> = emptyList()) = GroupField(key, tr(en, ar), c.text(key), multiline, toggle, choices = choices)
    private fun button(en: String, ar: String, value: String) = GroupButton(tr(en, ar), GroupAction.MENU_EDIT, value)
    fun open() { c.page = GroupPage.MENU_EDITOR }
    fun edit(value: String) {
        val pieces = value.split('|'); val nextType = pieces[0]
        if(c.page == GroupPage.MENU_ENTITY && nextType in listOf("variant", "option")) save(false)
        type = nextType; id = pieces.getOrElse(1) { "" }; parent = pieces.getOrElse(2) { "" }
        val item = menu.items.firstOrNull { it.id == id }
        val group = menu.optionGroups.firstOrNull { it.id == id }
        val category = menu.categories.firstOrNull { it.id == id }
        val variant = menu.items.firstOrNull { it.id == parent }?.variants?.firstOrNull { it.id == id }
        val option = menu.optionGroups.firstOrNull { it.id == parent }?.options?.firstOrNull { it.id == id }
        c.draft[GroupFieldKey.MENU_ENTITY_NAME] = when(type) { "item" -> item?.name; "group" -> group?.name; "category" -> category?.name; "variant" -> variant?.name; "option" -> option?.name; else -> error("Unknown menu field.") }.orEmpty()
        c.draft[GroupFieldKey.MENU_ENTITY_AR] = when(type) { "item" -> item?.nameAr; "group" -> group?.nameAr; "category" -> category?.nameAr; "variant" -> variant?.nameAr; else -> option?.nameAr }.orEmpty()
        c.draft[GroupFieldKey.MENU_ENTITY_DESCRIPTION] = item?.description.orEmpty()
        c.draft[GroupFieldKey.MENU_ENTITY_DESCRIPTION_AR] = item?.descriptionAr.orEmpty()
        c.draft[GroupFieldKey.MENU_ENTITY_PRICE] = Money.format(when(type) { "item" -> item?.basePriceMinor; "variant" -> variant?.priceMinor; else -> option?.priceDeltaMinor } ?: 0, "AED").substringAfter(' ')
        c.draft[GroupFieldKey.MENU_ENTITY_CATEGORY] = item?.categoryId ?: menu.categories.firstOrNull()?.id.orEmpty()
        c.draft[GroupFieldKey.MENU_ENTITY_AVAILABLE] = (item?.available ?: true).toString()
        c.draft[GroupFieldKey.MENU_ENTITY_MIN] = (group?.minSelections ?: 0).toString()
        c.draft[GroupFieldKey.MENU_ENTITY_MAX] = (group?.maxSelections ?: 1).toString()
        c.draft[GroupFieldKey.MENU_ENTITY_SORT] = (category?.sortOrder ?: menu.categories.size).toString()
        groups = item?.optionGroupIds.orEmpty()
        c.page = GroupPage.MENU_ENTITY
    }
    fun toggleGroup(id: String) { groups = if(id in groups) groups - id else groups + id }
    fun save(navigate: Boolean = true) {
        val name = c.text(GroupFieldKey.MENU_ENTITY_NAME).trim().also(MenuValidation::label)
        val ar = c.text(GroupFieldKey.MENU_ENTITY_AR).trim()
        if(ar.isNotEmpty()) MenuValidation.label(ar)
        if(id.isEmpty()) id = c.platform.uuid()
        fun price() = Money.parse(c.text(GroupFieldKey.MENU_ENTITY_PRICE), "AED")
        when(type) {
            "category" -> {
                val value = MenuCategory(id, name, c.text(GroupFieldKey.MENU_ENTITY_SORT).toIntOrNull() ?: error("Enter a whole-number sort position."), ar)
                set(menu.copy(categories = menu.categories.filterNot { it.id == id } + value))
            }
            "item" -> {
                val category = c.text(GroupFieldKey.MENU_ENTITY_CATEGORY)
                require(menu.categories.any { it.id == category }) { "Add and select a category first." }
                val old = menu.items.firstOrNull { it.id == id }
                val value = MenuItem(id, category, name, c.text(GroupFieldKey.MENU_ENTITY_DESCRIPTION), price(), c.flag(GroupFieldKey.MENU_ENTITY_AVAILABLE), old?.variants.orEmpty(), groups, ar, c.text(GroupFieldKey.MENU_ENTITY_DESCRIPTION_AR))
                set(menu.copy(items = menu.items.filterNot { it.id == id } + value))
            }
            "group" -> {
                val min = c.text(GroupFieldKey.MENU_ENTITY_MIN).toIntOrNull() ?: error("Enter a minimum selection count.")
                val max = c.text(GroupFieldKey.MENU_ENTITY_MAX).toIntOrNull() ?: error("Enter a maximum selection count.")
                require(min >= 0 && max >= min && max <= 30) { "Check the option selection limits." }
                val value = OptionGroup(id, name, min, max, menu.optionGroups.firstOrNull { it.id == id }?.options.orEmpty(), ar)
                set(menu.copy(optionGroups = menu.optionGroups.filterNot { it.id == id } + value))
            }
            "variant" -> set(menu.copy(items = menu.items.map { if(it.id != parent) it else it.copy(variants = it.variants.filterNot { it.id == id } + MenuVariant(id, name, price(), ar)) }))
            "option" -> set(menu.copy(optionGroups = menu.optionGroups.map { if(it.id != parent) it else it.copy(options = it.options.filterNot { it.id == id } + MenuOption(id, name, price(), ar)) }))
        }
        if(navigate) back()
    }
    fun remove(value: String) {
        val parts = value.split('|'); val target = parts.getOrElse(1) { "" }; val parentId = parts.getOrElse(2) { "" }
        set(when(parts[0]) {
            "category" -> { require(menu.items.none { it.categoryId == target }) { "Move or remove the category's items first." }; menu.copy(categories = menu.categories.filterNot { it.id == target }) }
            "group" -> menu.copy(optionGroups = menu.optionGroups.filterNot { it.id == target }, items = menu.items.map { it.copy(optionGroupIds = it.optionGroupIds - target) })
            "item" -> menu.copy(items = menu.items.filterNot { it.id == target })
            "variant" -> menu.copy(items = menu.items.map { if(it.id == parentId) it.copy(variants = it.variants.filterNot { it.id == target }) else it })
            "option" -> menu.copy(optionGroups = menu.optionGroups.map { if(it.id == parentId) it.copy(options = it.options.filterNot { it.id == target }) else it })
            else -> error("Unknown menu field.")
        })
    }
    fun back() {
        if(c.page == GroupPage.MENU_EDITOR) c.page = GroupPage.RESTAURANT
        else if(type == "variant") edit("item|$parent")
        else if(type == "option") edit("group|$parent")
        else c.page = GroupPage.MENU_EDITOR
    }
    private fun row(kind: String, key: String, title: String, detail: String = "", parentId: String = "") = GroupCard("menu-edit:$kind:$key", title, detail,
        buttons = listOf(button("Edit", "تعديل", "$kind|$key|$parentId"), GroupButton(tr("Remove", "إزالة"), GroupAction.MENU_REMOVE, "$kind|$key|$parentId", destructive = true)))
    fun content(): GroupFlowContent {
        if(c.page == GroupPage.MENU_EDITOR) return GroupFlowContent(cards =
            menu.categories.sortedBy { it.sortOrder }.map { row("category", it.id, it.localizedName(c.library.language)) } +
            menu.items.map { row("item", it.id, it.localizedName(c.library.language), Money.format(it.basePriceMinor, "AED")) } +
            menu.optionGroups.map { row("group", it.id, it.localizedName(c.library.language), "${it.minSelections}–${it.maxSelections}") },
            buttons = listOf(button("Add category", "إضافة قسم", "category|"), button("Add item", "إضافة صنف", "item|"), button("Add extras group", "إضافة مجموعة إضافات", "group|")))
        val fields = mutableListOf(field(GroupFieldKey.MENU_ENTITY_NAME, "Name · English", "الاسم · إنجليزي"), field(GroupFieldKey.MENU_ENTITY_AR, "Name · Arabic", "الاسم · عربي"))
        val cards = mutableListOf<GroupCard>(); val buttons = mutableListOf<GroupButton>()
        when(type) {
            "category" -> fields += field(GroupFieldKey.MENU_ENTITY_SORT, "Sort position", "ترتيب العرض")
            "item" -> {
                fields += field(GroupFieldKey.MENU_ENTITY_CATEGORY, "Category", "القسم", choices = menu.categories.map { GroupChoice(it.id, it.localizedName(c.library.language)) })
                fields += field(GroupFieldKey.MENU_ENTITY_DESCRIPTION, "Description · English", "الوصف · إنجليزي", multiline = true)
                fields += field(GroupFieldKey.MENU_ENTITY_DESCRIPTION_AR, "Description · Arabic", "الوصف · عربي", multiline = true)
                fields += field(GroupFieldKey.MENU_ENTITY_AVAILABLE, "Available", "متاح", toggle = true)
                fields += field(GroupFieldKey.MENU_ENTITY_PRICE, "Base price · AED", "السعر الأساسي · درهم")
                cards += menu.optionGroups.map { group -> GroupCard("attach:${group.id}", group.localizedName(c.library.language), buttons = listOf(GroupButton(if(group.id in groups) tr("✓ Included · remove", "✓ مضمنة · إزالة") else tr("Include extras", "تضمين الإضافات"), GroupAction.MENU_TOGGLE_GROUP, group.id))) }
                cards += menu.items.firstOrNull { it.id == id }?.variants.orEmpty().map { row("variant", it.id, it.localizedName(c.library.language), Money.format(it.priceMinor, "AED"), id) }
                if(id.isNotEmpty()) buttons += button("Add size", "إضافة حجم", "variant||$id")
            }
            "group" -> {
                fields += field(GroupFieldKey.MENU_ENTITY_MIN, "Minimum selections", "أقل عدد اختيارات")
                fields += field(GroupFieldKey.MENU_ENTITY_MAX, "Maximum selections", "أقصى عدد اختيارات")
                cards += menu.optionGroups.firstOrNull { it.id == id }?.options.orEmpty().map { row("option", it.id, it.localizedName(c.library.language), Money.format(it.priceDeltaMinor, "AED"), id) }
                if(id.isNotEmpty()) buttons += button("Add extra", "إضافة اختيار", "option||$id")
            }
            else -> fields += field(GroupFieldKey.MENU_ENTITY_PRICE, if(type == "variant") "Size price · AED" else "Extra price · AED", if(type == "variant") "سعر الحجم · درهم" else "سعر الإضافة · درهم")
        }
        buttons += GroupButton(tr("Save menu entry", "حفظ بيانات القائمة"), GroupAction.MENU_SAVE, primary = true)
        return GroupFlowContent(fields, cards, buttons)
    }
}
