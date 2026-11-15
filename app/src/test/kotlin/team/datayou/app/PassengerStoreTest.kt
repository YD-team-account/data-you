package team.datayou.app

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import team.datayou.app.data.PassengerStore
import team.datayou.engine.model.PassengerType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PassengerStoreTest {

    @Test
    fun `처음에는 아무도 고르지 않은 상태로 열린다`() {
        assertEquals(emptySet<PassengerType>(), PassengerStore(ApplicationProvider.getApplicationContext()).load())
    }

    @Test
    fun `고른 동승자는 다음 주행 팝업에서도 그대로 열린다`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        PassengerStore(ctx).save(setOf(PassengerType.DOG, PassengerType.CHILD))
        assertEquals(setOf(PassengerType.CHILD, PassengerType.DOG), PassengerStore(ctx).load())
        PassengerStore(ctx).save(emptySet())
        assertEquals(emptySet<PassengerType>(), PassengerStore(ctx).load())
    }
}
