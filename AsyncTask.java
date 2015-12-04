package com.infra.rest.util.thread;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AsyncTask<Request,Response> {

	static final Logger logger = LoggerFactory.getLogger(AsyncTask.class);
	
	static final int THREADCOUNT=Runtime.getRuntime().availableProcessors();
	protected Request _request;

	private  Future<Response> _future;
	static private ExecutorService _executor;

	protected AsyncTask()
	{

	}

	protected abstract void beforeExecute();
	protected abstract Response doInBackground(Request request);
	protected abstract void didFinishExecute(Response response);

	protected void didCancel()
	{

	}

	protected ExecutorService getExecutorService()
	{
		if(_executor==null || _executor.isTerminated()==true)
		{
			_executor=Executors.newFixedThreadPool(THREADCOUNT);
		
			logger.debug("AsyncTask ExecutorService created threadpool count:"+THREADCOUNT);
			
		}
		
		return _executor;
	}

	protected final Response getResponse() throws InterruptedException, ExecutionException
	{
		return _future.get();
	}

	public void execute(final Request obj)
	{
		//logger.debug("task is executed");
		
		beforeExecute();

		_future=executeCallback(obj);

		Handler.instance().execute(this);
		
	}

	public boolean isFinish()
	{
		return _future.isDone();
	}
	public boolean cancel()
	{
		if(_future.isCancelled()==false)return _future.cancel(true);

		return false;
	}

	protected Future<Response> executeCallback(final Request obj)
	{
		Future<Response> future=getExecutorService().submit(new Callable<Response>()
		{
			@Override
			public Response call() throws Exception {
				// TODO 
				return doInBackground(obj);
			}
		});

		return future;
	}

	private void finished(Object res)
	{
		if(_future.isCancelled())
		{
			didCancel();
			logger.debug("task is canceled");
			
		}else{

			@SuppressWarnings("unchecked")
			Response response=(Response)res;

			didFinishExecute(response);
		
			logger.debug("task is finished res : " +res);
			
		}
	}

	public static int nextInt = 0;
	@SuppressWarnings("rawtypes")
	private static final class Handler implements Runnable
	{
		static Handler _handler;
		static Thread _thread=null;
		static boolean isRunning=true;
		static final int _handlerWait=3000;//3sec대기

		public static Handler instance()
		{
			if(_handler==null)_handler=new Handler();
			return _handler;
		}

		final LinkedList<AsyncTask> _tasks = new LinkedList<AsyncTask>();

		private Thread createThread()
		{
			Thread thread=new Thread(this);
			thread.setName("ASyncTaskThread-"+thread.getId());
			//logger.debug("AsyncTaskThread Created :"+thread.getName());

			return thread;
		}
		
		public void execute(AsyncTask a)
		{
			//logger.debug("handler execute task :"+a.toString());
			add(a);

			if(_thread==null)
			{
				_thread=this.createThread();
				_thread.start();
			}

			if(_thread.getState()==Thread.State.TIMED_WAITING)
			{

				//logger.debug("handler thread is waitting.. will notify");
				synchronized (_thread) {
					_thread.notify();
				}


			}else if(_thread.getState()==Thread.State.TERMINATED)
			{
				//logger.debug("handler thread is terminated...");
				_thread=this.createThread();
				_thread.start();

			}



		}


		@Override
		public void run() {

			//System.out.println("handler run ");
			while(isRunning)
			{

				synchronized (_tasks) {

					ArrayList<AsyncTask<?,?>> removeList=new ArrayList<AsyncTask<?,?>>(); 

					for(AsyncTask<?, ?> task: _tasks)
					{
						if(task._future!=null && task._future.isDone())
						{
							Object res=null;
							try {

								res=task._future.get();

							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (ExecutionException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							task.finished(res);

							removeList.add(task);

						}
					}

					for(AsyncTask<?, ?> task:removeList)
					{
						//테스크 삭제
						_tasks.remove(task);
					}

				}

				try {

					Thread.sleep(1);

				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();

				}

				if(hasTask()==false)
				{
					synchronized (_thread) {
						try {

							_thread.wait(_handlerWait);

							if(hasTask()==false)
							{
								//logger.debug("No Task found... shutdown thread : "+Thread.currentThread().getName());	
								isRunning=false;
							}

						} catch (InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}

					}
				}

			}


			if(_executor.isShutdown()==false)_executor.shutdownNow();

			while(_executor.isTerminated()==false)
			{
				try {

					Thread.sleep(10);

				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();

				}
			}

			logger.debug("Active Thread : " +Thread.activeCount()+" Current Thread : "+Thread.currentThread()+" is shutdown...");	
			logger.debug("executor teminated. : " +_executor.isTerminated());	
			
		}

		protected synchronized boolean hasTask()
		{
			return _tasks.size()>0;
		}

		protected synchronized void add(AsyncTask a)
		{
			_tasks.offerLast(a);

		}



	}

}
