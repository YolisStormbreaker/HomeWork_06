package otus.homework.reactivecats

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class CatsViewModel(
    private val catsService: CatsService,
    private val localCatFactsGenerator: LocalCatFactsGenerator,
    context: Context
) : ViewModel(), LifecycleObserver {

    private val _catsLiveData = MutableLiveData<Result>()
    val catsLiveData: LiveData<Result> = _catsLiveData

    private val disposables = CompositeDisposable()

    init {
        disposables.add(
            catsService
                .getCatFact()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _catsLiveData.value = Success(it)
                }, { ex ->
                    when (ex) {
                        is HttpException -> {
                            _catsLiveData.value = Error(ex.message ?: context.getString(
                                R.string.default_error_text
                            ))
                        }
                        else -> _catsLiveData.value = ServerError
                    }
                })
        )
    }

    fun getFacts(onItemCollected: (Fact) -> Unit) {
        disposables.add(
            catsService
                .getCatFact()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .delay(CAT_GET_FACTS_PERIOD_MILLISECONDS, TimeUnit.MILLISECONDS)
                .repeat()
                .onErrorResumeNext(localCatFactsGenerator.generateCatFact().toObservable())
                .subscribe(
                    { fact ->
                        onItemCollected(fact)
                    }, { error ->
                        Log.e(CAT_VIEW_MODEL_LOG_LABEL, error.message ?: CAT_VIEW_MODEL_LOG_DEFAULT_MSG)
                    }
                )
        )
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
    }

    companion object {
        private const val CAT_GET_FACTS_PERIOD_MILLISECONDS = 2000L
        private const val CAT_VIEW_MODEL_LOG_LABEL = "CAT_VIEW_MODEL"
        private const val CAT_VIEW_MODEL_LOG_DEFAULT_MSG = "getCatFact() - Error occurred"
    }
}

class CatsViewModelFactory(
    private val catsRepository: CatsService,
    private val localCatFactsGenerator: LocalCatFactsGenerator,
    private val context: Context
) :
    ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        CatsViewModel(catsRepository, localCatFactsGenerator, context) as T
}

sealed class Result
data class Success(val fact: Fact) : Result()
data class Error(val message: String) : Result()
object ServerError : Result()