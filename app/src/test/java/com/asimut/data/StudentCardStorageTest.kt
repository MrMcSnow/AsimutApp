package com.asimut.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.asimut.models.StudentCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudentCardStorageTest {

    @Test
    fun `getCards ignores corrupt data and clears storage`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val storage = StudentCardStorage(context)
        val preferences = context.getSharedPreferences("student_cards", Context.MODE_PRIVATE)
        preferences.edit().putString("cards", "not a json array").apply()

        val cards = storage.getCards()

        assertEquals(emptyList<StudentCard>(), cards)
        assertNull(preferences.getString("cards", null))
    }
}
