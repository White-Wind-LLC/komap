package ua.wwind.komap.samples.factory

import ua.wwind.komap.Komap
import ua.wwind.komap.KomapFactory

data class AttributeId(val value: Long)

data class AttributeEntity(
    val id: Long,
    val name: String,
    val isGroup: Boolean,
    val group: Long?,
    val type: String?,
    val identifier: String,
    val tables: List<String>?,
    val values: List<List<String>>?,
    val number: Long,
    val deleted: Boolean,
    val version: Short,
)

data class NewAttributeEntity(
    val name: String,
    val group: Long?,
    val isGroup: Boolean,
    val type: String?,
    val identifier: String,
    val tables: List<String>?,
    val values: List<List<String>>?,
    val deleted: Boolean,
    val version: Short,
)

@Komap(
    from = [AttributeEntity::class],
    to = [NewAttributeEntity::class],
    factoryQualifiers = [AttributeGroup.FACTORY_NAME]
)
data class AttributeGroup(
    val name: String,
    val number: Long,
    val group: AttributeId?,
    val version: Short,
    val deleted: Boolean,
) {
    companion object {
        const val FACTORY_NAME = "AttributeGroup"
    }
}

@Komap(
    to = [NewAttributeEntity::class],
    factoryQualifiers = [AttributeItem.FACTORY_NAME]
)
data class AttributeItem(
    val name: String,
    val number: Long,
    val type: String?,
    val identifier: String,
    val tables: List<String>?,
    val values: List<AttributeValue>?,
    val group: AttributeId?,
    val version: Short,
    val deleted: Boolean,
) {
    companion object {
        const val FACTORY_NAME = "AttributeItem"

        @Komap(
            from = [AttributeEntity::class]
        )
        fun attributeItemFactory(
            name: String,
            number: Long,
            type: String?,
            identifier: String,
            tables: List<String>?,
            values: List<List<String>>?,
            group: AttributeId?,
            version: Short,
            deleted: Boolean
        ) = AttributeItem(
            name = name,
            number = number,
            type = type ?: "STRING",
            identifier = identifier,
            tables = tables ?: emptyList(),
            values = if (type == "ENUM") {
                values?.mapNotNull { items ->
                    items.firstOrNull()?.let { it to (items.getOrNull(1) ?: "") }
                }?.map {
                    AttributeValue(it.first, it.second)
                }
            } else null,
            group = group,
            version = version,
            deleted = deleted
        )
    }
}

data class AttributeValue(
    val value: String,
    val name: String,
)

@KomapFactory(AttributeGroup.FACTORY_NAME)
internal fun newAttributeGroupEntity(
    name: String,
    group: Long?,
    deleted: Boolean,
    version: Short,
): NewAttributeEntity = NewAttributeEntity(
    name = name,
    group = group,
    isGroup = true,
    type = null,
    identifier = "",
    tables = null,
    values = null,
    deleted = deleted,
    version = version,
)

@KomapFactory(AttributeItem.FACTORY_NAME)
internal fun newAttributeItemEntity(
    name: String,
    group: Long?,
    deleted: Boolean,
    version: Short,
    type: String?,
    identifier: String,
    tables: List<String>?,
    values: List<AttributeValue>?,
): NewAttributeEntity = NewAttributeEntity(
    name = name,
    group = group,
    isGroup = false,
    type = type,
    identifier = identifier,
    tables = tables,
    values = values?.toListOfStrings(),
    deleted = deleted,
    version = version,
)

fun List<AttributeValue>.toListOfStrings(): List<List<String>> = map { attribute ->
    buildList {
        add(attribute.value)
        if (attribute.name.isNotBlank()) add(attribute.name)
    }
}

fun main() {
    val groupAttribute = AttributeEntity(
        id = 1L,
        name = "Clothes",
        isGroup = true,
        group = null,
        type = null,
        identifier = "",
        tables = null,
        values = null,
        number = 1L,
        deleted = false,
        version = 1
    )

    val genderItem = AttributeEntity(
        id = 2L,
        name = "Gender Item",
        isGroup = false,
        group = 1L,
        type = "ENUM",
        identifier = "gender",
        tables = listOf("users", "profiles", "products"),
        values = listOf(listOf("M", "Male"), listOf("F", "Female"), listOf("U", "Unisex")),
        number = 2L,
        deleted = false,
        version = 1
    )

    // Using generated mapping function for AttributeItem
    val mappedItem = genderItem.toAttributeItem()

    println("Mapped AttributeItem: $mappedItem")

    // Using generated mapping function for AttributeGroup
    val mappedGroup = groupAttribute.toAttributeGroup()

    println("Mapped AttributeGroup: $mappedGroup")
}
