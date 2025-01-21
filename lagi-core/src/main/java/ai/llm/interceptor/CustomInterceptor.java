package ai.llm.interceptor;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

public class CustomInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();

        //移除`Accept: text/event-stream`头
        Request modifiedRequest = originalRequest.newBuilder()
                .removeHeader("Accept")
                .build();
        return chain.proceed(modifiedRequest);
    }
}
