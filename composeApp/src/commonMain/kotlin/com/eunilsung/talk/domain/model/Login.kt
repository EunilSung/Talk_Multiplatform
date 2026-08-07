package com.eunilsung.talk.domain.model

sealed class Login {
    class LoginRequest(
        id: String = "",
        pw: String = "",
        val isSavePw: Boolean = false
    ) {
        val id: String = id.trim()
        val pw: String = pw.trim()

        fun copy(id: String = this.id, pw: String = this.pw, isSavePw: Boolean = this.isSavePw): LoginRequest {
            return LoginRequest(id, pw, isSavePw)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is LoginRequest) return false
            if (id != other.id) return false
            if (pw != other.pw) return false
            if (isSavePw != other.isSavePw) return false
            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + pw.hashCode()
            result = 31 * result + isSavePw.hashCode()
            return result
        }
    }

    sealed class LoginResult {
        data object Success : LoginResult()
        data class Error(val message: String) : LoginResult()
        data class Duplicate(val message: String) : LoginResult()
    }
}