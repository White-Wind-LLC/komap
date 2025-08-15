package ua.wwind.komap.samples.basic

import ua.wwind.komap.IgnoreInMapping
import ua.wwind.komap.Komap
import ua.wwind.komap.MapName

// Basic domain model
@Komap(from = [ApiUser::class], to = [ApiUser::class])
data class User(
    @MapName(name = "userId", forClasses = [ApiUser::class])
    val id: Long,
    @MapName(name = "name", forClasses = [ApiUser::class])
    val fullName: String,
    val email: Email,
)

// Transport/API model used in a different layer
data class ApiUser(
    val userId: Long,
    val name: String,
    val email: String,
    // This field is not present in the domain model and will be exposed
    // as a required parameter in the generated mapping function.
    @IgnoreInMapping val role: String,
)

// Simple value type to demonstrate @KomapProvider conversions in providers package
data class Email(val value: String)

fun main() {
    // Create a User instance
    val user = User(
        id = 1L,
        fullName = "John Doe",
        email = Email("john.doe@example.com")
    )

    // Map User to ApiUser using the generated mapper
    // The 'role' parameter needs to be provided since it's not part of the User model
    val apiUser = user.toApiUser(role = "USER")

    println("Mapped ApiUser: $apiUser")

    // Create a list of User instances
    val users = listOf(
        User(
            id = 2L,
            fullName = "Jane Smith",
            email = Email("jane.smith@example.com")
        ),
        User(
            id = 3L,
            fullName = "Bob Johnson",
            email = Email("bob.johnson@example.com")
        )
    )

    // Map list of Users to list of ApiUsers
    val apiUsers = users.toApiUser { "ADMIN" }

    println("Mapped ApiUsers: $apiUsers")
}
