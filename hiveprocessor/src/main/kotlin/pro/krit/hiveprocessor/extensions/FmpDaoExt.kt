// Copyright (c) 2021 Aleksandr Minkin aka Rasalexman (sphc@yandex.ru)
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software
// and associated documentation files (the "Software"), to deal in the Software without restriction,
// including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
// subject to the following conditions:
// The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
// WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
// IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
// WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
// THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package pro.krit.hiveprocessor.extensions
import com.mobrun.plugin.models.StatusSelectTable
import pro.krit.hiveprocessor.base.IDao

inline fun <reified E : Any, reified S : StatusSelectTable<E>> IDao.IFmpDao<E, S>.flowable(
    where: String = "",
    limit: Int = 0,
    withStart: Boolean = true,
    emitDelay: Long = 100L,
    withDistinct: Boolean = false
) = DaoInstance.flowable<E, S>(this, where, limit, withStart, emitDelay, withDistinct)

inline fun <reified E : Any, reified S : StatusSelectTable<E>> IDao.IFmpDao<E, S>.select(
    where: String = "",
    limit: Int = 0
): List<E> = DaoInstance.select<E, S>(this, where, limit)

suspend inline fun <reified E : Any, reified S : StatusSelectTable<E>> IDao.IFmpDao<E, S>.selectAsync(
    where: String = "",
    limit: Int = 0
): List<E> {
    return DaoInstance.selectAsync<E, S>(this, where, limit)
}

inline fun <reified E : Any, reified S : StatusSelectTable<E>> IDao.IFmpDao<E, S>.selectResult(
    where: String = "",
    limit: Int = 0
): Result<List<E>> {
    return DaoInstance.selectResult<E, S>(this, where, limit)
}
