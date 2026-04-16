import { useState, useEffect } from 'react';
import { useRouter } from 'next/router';
import { useAuth } from '@clerk/nextjs';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import {
  PieChart, Pie, Cell, BarChart, Bar, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts';
import Layout from '../components/Layout';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import { API_URL } from '../lib/config';
import Head from 'next/head';

interface Job {
  id: string;
  created_at: string;
  status: string;
  job_type: string;
  report_payload?: {
    agent: string;
    content: string;
    generated_at: string;
  };
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  charts_payload?: Record<string, any> | null;  // Charter stores charts with dynamic keys
  retirement_payload?: {
    agent: string;
    analysis: string;
    generated_at: string;
  };
  error_message?: string;
}

interface JobListItem {
  id: string;
  created_at: string;
  status: string;
  job_type: string;
}

type TabType = 'overview' | 'charts' | 'retirement';

// Color palette for charts
const COLORS = [
  '#1178A8', // primary
  '#753991', // AI accent
  '#FFB707', // accent fill
  '#062147', // dark
  '#60A5FA', // light blue
  '#A78BFA', // light purple
  '#FBBF24', // yellow
  '#34D399', // green
  '#F87171', // red
  '#94A3B8', // gray
];

export default function Analysis() {
  const router = useRouter();
  const { getToken } = useAuth();
  const { job_id } = router.query;
  const [job, setJob] = useState<Job | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<TabType>('overview');
  const [fetchingLatest, setFetchingLatest] = useState(false);

  useEffect(() => {
    const loadJob = async (jobId: string) => {
      try {
        const token = await getToken();
        const response = await fetch(`${API_URL}/api/jobs/${jobId}`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        });

        if (response.ok) {
          const jobData = await response.json();
          setJob(jobData);
        } else {
          console.error('Failed to fetch job');
        }
      } catch (error) {
        console.error('Error fetching job:', error);
      } finally {
        setLoading(false);
      }
    };

    const loadLatestJob = async () => {
      setFetchingLatest(true);
      try {
        const token = await getToken();
        // First, get the list of jobs to find the latest completed one
        const response = await fetch(`${API_URL}/api/jobs`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        });

        if (response.ok) {
          const data = await response.json();
          const jobs: JobListItem[] = data.jobs || [];
          // Find the latest completed job
          const latestCompletedJob = jobs
            .filter(j => j.status === 'completed')
            .sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime())[0];

          if (latestCompletedJob) {
            // Load the full job details
            await loadJob(latestCompletedJob.id);
            // Update the URL to include the job_id without causing a page reload
            router.replace(`/analysis?job_id=${latestCompletedJob.id}`, undefined, { shallow: true });
          } else {
            setLoading(false);
          }
        } else {
          setLoading(false);
        }
      } catch (error) {
        console.error('Error fetching latest job:', error);
        setLoading(false);
      } finally {
        setFetchingLatest(false);
      }
    };

    if (job_id) {
      loadJob(job_id as string);
    } else if (router.isReady) {
      // Router is ready but no job_id provided - fetch the latest analysis
      loadLatestJob();
    }
  }, [job_id, router.isReady, getToken, router]);


  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('en-US', {
      month: 'long',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  if (loading) {
    return (
      <Layout>
        <div className="min-h-screen bg-surface py-8">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <Card tier="raised" className="text-center py-12">
              <div className="animate-pulse">
                <div className="h-8 bg-gray-200 rounded w-1/3 mx-auto mb-4"></div>
                <div className="h-4 bg-gray-200 rounded w-1/2 mx-auto"></div>
              </div>
            </Card>
          </div>
        </div>
      </Layout>
    );
  }

  if (!job) {
    return (
      <Layout>
        <div className="min-h-screen bg-surface py-8">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <Card tier="raised" className="text-center py-12">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">
                {fetchingLatest ? 'Loading Latest Analysis...' : 'No Analysis Available'}
              </h2>
              <p className="text-gray-600 mb-6">
                {fetchingLatest
                  ? 'Please wait while we load your latest analysis.'
                  : 'You have not completed any analyses yet. Start a new analysis to see results here.'}
              </p>
              {!fetchingLatest && (
                <Button
                  onClick={() => router.push('/advisor-team')}
                  variant="primary"
                >
                  Start New Analysis
                </Button>
              )}
            </Card>
          </div>
        </div>
      </Layout>
    );
  }

  if (job.status === 'running' || job.status === 'pending') {
    return (
      <Layout>
        <div className="min-h-screen bg-surface py-8">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <Card tier="raised" className="text-center py-12">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">Analysis In Progress</h2>
              <p className="text-gray-600 mb-6">Your analysis is still being processed. Please check back in a few moments.</p>
              <div className="flex justify-center space-x-2 mb-6">
                <div className="w-3 h-3 bg-ai-accent rounded-full animate-pulse"></div>
                <div className="w-3 h-3 bg-ai-accent rounded-full animate-pulse delay-75"></div>
                <div className="w-3 h-3 bg-ai-accent rounded-full animate-pulse delay-150"></div>
              </div>
              <Button
                onClick={() => window.location.reload()}
                variant="primary"
              >
                Refresh
              </Button>
            </Card>
          </div>
        </div>
      </Layout>
    );
  }

  if (job.status === 'failed') {
    return (
      <Layout>
        <div className="min-h-screen bg-surface py-8">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <Card tier="raised" className="py-12">
              <h2 className="text-2xl font-bold text-error mb-4">Analysis Failed</h2>
              <p className="text-gray-600 mb-4">The analysis encountered an error and could not be completed.</p>
              {job.error_message && (
                <div className="bg-error-light border border-red-200 rounded-lg p-4 mb-6">
                  <p className="text-sm text-red-800">{job.error_message}</p>
                </div>
              )}
              <Button
                onClick={() => router.push('/advisor-team')}
                variant="primary"
              >
                Try Another Analysis
              </Button>
            </Card>
          </div>
        </div>
      </Layout>
    );
  }


  // Tab content renderers
  const renderOverview = () => {
    const report = job?.report_payload?.content;
    if (!report) {
      return (
        <div className="text-center py-12 text-gray-500">
          No portfolio report available.
        </div>
      );
    }

    return (
      <div className="prose prose-lg max-w-none">
        <ReactMarkdown
          remarkPlugins={[remarkGfm, remarkBreaks]}
          components={{
            h1: ({children}) => <h1 className="text-3xl font-bold mb-4 text-gray-900">{children}</h1>,
            h2: ({children}) => <h2 className="text-2xl font-semibold mb-3 text-gray-800 mt-6">{children}</h2>,
            h3: ({children}) => <h3 className="text-xl font-medium mb-2 text-gray-700 mt-4">{children}</h3>,
            ul: ({children}) => <ul className="list-disc ml-6 mb-4 space-y-1">{children}</ul>,
            ol: ({children}) => <ol className="list-decimal ml-6 mb-4 space-y-1">{children}</ol>,
            li: ({children}) => <li className="text-gray-700">{children}</li>,
            p: ({children}) => <p className="mb-4 text-gray-700 leading-relaxed">{children}</p>,
            table: ({children}) => (
              <div className="overflow-x-auto mb-6">
                <table className="w-full border-collapse">{children}</table>
              </div>
            ),
            thead: ({children}) => <thead className="bg-gray-100">{children}</thead>,
            th: ({children}) => <th className="p-3 text-left font-semibold border border-gray-300">{children}</th>,
            td: ({children}) => <td className="p-3 border border-gray-300">{children}</td>,
            strong: ({children}) => <strong className="font-semibold text-gray-900">{children}</strong>,
            blockquote: ({children}) => (
              <blockquote className="border-l-4 border-primary pl-4 my-4 italic text-gray-600">
                {children}
              </blockquote>
            ),
          }}
        >
          {report}
        </ReactMarkdown>
      </div>
    );
  };

  const renderCharts = () => {
    const chartsPayload = job?.charts_payload;
    if (!chartsPayload || Object.keys(chartsPayload).length === 0) {
      return (
        <div className="text-center py-12 text-gray-500">
          No chart data available.
        </div>
      );
    }

    // Helper function to format chart title from key
    const formatTitle = (key: string): string => {
      return key
        .split('_')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
    };

    // Helper function to determine chart type based on data structure or chart metadata
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const getChartType = (chartData: any): 'pie' | 'donut' | 'bar' | 'horizontalBar' | 'line' => {
      // If the charter agent specifies a type, use it directly if supported
      if (chartData.type) {
        const supportedTypes = ['pie', 'donut', 'bar', 'horizontalBar', 'line'];
        if (supportedTypes.includes(chartData.type)) {
          return chartData.type;
        }
        // Map variations to supported types
        const typeMap: Record<string, 'pie' | 'donut' | 'bar' | 'horizontalBar' | 'line'> = {
          'column': 'bar',
          'area': 'line'
        };
        if (typeMap[chartData.type]) {
          return typeMap[chartData.type];
        }
      }

      // Otherwise, make an intelligent guess based on the data
      // If data has dates/time series, use line chart
      if (chartData.data?.[0]?.date || chartData.data?.[0]?.year) return 'line';

      // If data represents parts of a whole (has percentages or small dataset), use pie
      if (chartData.data?.length <= 10 && chartData.data?.[0]?.value) return 'pie';

      // Default to bar chart for other cases
      return 'bar';
    };

    // Dynamically render all charts provided by the charter agent
    const chartEntries = Object.entries(chartsPayload);

    return (
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
        {chartEntries.map(([key, chartData]: [string, any], index: number) => {
          // Skip if no data
          if (!chartData?.data || chartData.data.length === 0) return null;

          const chartType = getChartType(chartData);
          const title = chartData.title || formatTitle(key);

          return (
            <Card key={key} tier="raised" className="chart-enter visible"
              style={{ transitionDelay: `${index * 100}ms` }}>
              {/* Chart header */}
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold text-dark">{title}</h3>
                <span className="text-xs text-gray-400 bg-gray-50 px-2 py-0.5 rounded">
                  {chartType}
                </span>
              </div>

              {/* Chart body — fixed height to prevent CLS */}
              <div className="h-[300px]">
              <ResponsiveContainer width="100%" height="100%">
                {chartType === 'pie' || chartType === 'donut' ? (
                  <PieChart>
                    <Pie
                      data={chartData.data}
                      cx="50%"
                      cy="50%"
                      labelLine={false}
                      label
                      outerRadius={100}
                      innerRadius={chartType === 'donut' ? 60 : 0}  // Donut has inner radius
                      fill="#8884d8"
                      dataKey="value"
                    >
                      {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
                      {chartData.data.map((entry: any, idx: number) => (
                        <Cell key={`cell-${idx}`} fill={entry.color || COLORS[idx % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip formatter={(value: number) => `\u20b9${value.toLocaleString('en-IN')}`} />
                  </PieChart>
                ) : chartType === 'horizontalBar' ? (
                  // For horizontal bars, just use regular vertical bars with rotated labels
                  // Recharts horizontal layout can be problematic
                  <BarChart
                    data={chartData.data}
                    margin={{ left: 10, right: 30, top: 5, bottom: 60 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis
                      dataKey="name"
                      angle={-45}
                      textAnchor="end"
                      interval={0}
                      height={60}
                    />
                    <YAxis
                      tickFormatter={(value) => `\u20b9${(value/1000).toFixed(0)}k`}
                    />
                    <Tooltip formatter={(value: number) => `\u20b9${value.toLocaleString('en-IN')}`} />
                    <Bar dataKey="value">
                      {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
                      {chartData.data?.map((entry: any, index: number) => (
                        <Cell key={`cell-${index}`} fill={entry.color || COLORS[index % COLORS.length]} />
                      ))}
                    </Bar>
                  </BarChart>
                ) : chartType === 'bar' ? (
                  <BarChart data={chartData.data}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="name" angle={-45} textAnchor="end" height={80} />
                    <YAxis tickFormatter={(value) => `\u20b9${(value/1000).toFixed(0)}k`} />
                    <Tooltip formatter={(value: number) => `\u20b9${value.toLocaleString('en-IN')}`} />
                    <Bar dataKey="value" fill={chartData.color || COLORS[0]} />
                  </BarChart>
                ) : (
                  // Line chart for time series data
                  <LineChart data={chartData.data}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey={chartData.xKey || "year"} />
                    <YAxis tickFormatter={(value) => `\u20b9${(value/1000).toFixed(0)}k`} />
                    <Tooltip formatter={(value: number) => `\u20b9${value.toLocaleString('en-IN')}`} />
                    <Line type="monotone" dataKey="value" stroke={COLORS[0]} strokeWidth={2} />
                  </LineChart>
                )}
              </ResponsiveContainer>
              </div>

              {/* Add legend for pie/donut charts with many items */}
              {(chartType === 'pie' || chartType === 'donut') && chartData.data.length > 6 && (
                <div className="mt-4 grid grid-cols-2 gap-2">
                  {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
                  {chartData.data.map((entry: any, idx: number) => (
                    <div key={entry.name} className="flex items-center text-sm">
                      <div
                        className="w-3 h-3 rounded-full mr-2 shrink-0"
                        style={{ backgroundColor: entry.color || COLORS[idx % COLORS.length] }}
                      />
                      <span className="text-gray-600 truncate">{entry.name}</span>
                    </div>
                  ))}
                </div>
              )}
            </Card>
          );
        })}
      </div>
    );
  };

  const renderRetirement = () => {
    const retirement = job?.retirement_payload;
    if (!retirement) {
      return (
        <div className="text-center py-12 text-gray-500">
          No retirement projection available.
        </div>
      );
    }

    // Backend provides 'analysis' as markdown text
    const retirementAnalysis = retirement.analysis;

    return (
      <div className="space-y-8">
        {/* Analysis Section */}
        {retirementAnalysis && (
          <div className="bg-ai-accent/10 border border-ai-accent/20 rounded-lg p-6">
            <div className="prose prose-lg max-w-none">
              <ReactMarkdown
                remarkPlugins={[remarkGfm, remarkBreaks]}
                components={{
                  h2: ({children}) => <h2 className="text-2xl font-semibold mb-3 text-gray-800">{children}</h2>,
                  h3: ({children}) => <h3 className="text-xl font-medium mb-2 text-gray-700">{children}</h3>,
                  p: ({children}) => <p className="text-gray-700 leading-relaxed mb-4">{children}</p>,
                  strong: ({children}) => <strong className="font-semibold text-gray-900">{children}</strong>,
                  ul: ({children}) => <ul className="list-disc ml-6 mt-2 space-y-1">{children}</ul>,
                  li: ({children}) => <li className="text-gray-700">{children}</li>,
                }}
              >
                {retirementAnalysis}
              </ReactMarkdown>
            </div>
          </div>
        )}

      </div>
    );
  };

  return (
    <>
      <Head>
        <title>Analysis - Alex AI Financial Advisor</title>
      </Head>
      <Layout>
      <div className="min-h-screen bg-surface py-8">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          {/* Header */}
          <Card tier="raised" className="mb-8">
            <div className="flex items-center justify-between">
              <div>
                <h1 className="text-3xl font-bold text-dark mb-2">Portfolio Analysis Results</h1>
                <p className="text-gray-600">
                  Completed on {formatDate(job.created_at)}
                </p>
              </div>
              <Button
                onClick={() => router.push('/advisor-team')}
                variant="primary"
              >
                New Analysis
              </Button>
            </div>
          </Card>

          {/* Tabs */}
          <Card tier="raised" padding="p-0" className="mb-8">
            <div className="border-b border-border">
              <nav className="flex -mb-px" role="tablist">
                <button
                  role="tab"
                  aria-selected={activeTab === 'overview'}
                  onClick={() => setActiveTab('overview')}
                  className={`py-3 px-8 border-b-2 font-medium text-sm transition-colors ${
                    activeTab === 'overview'
                      ? 'border-primary text-primary'
                      : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                  }`}
                >
                  Overview
                </button>
                <button
                  role="tab"
                  aria-selected={activeTab === 'charts'}
                  onClick={() => setActiveTab('charts')}
                  className={`py-3 px-8 border-b-2 font-medium text-sm transition-colors ${
                    activeTab === 'charts'
                      ? 'border-primary text-primary'
                      : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                  }`}
                >
                  Charts
                </button>
                <button
                  role="tab"
                  aria-selected={activeTab === 'retirement'}
                  onClick={() => setActiveTab('retirement')}
                  className={`py-3 px-8 border-b-2 font-medium text-sm transition-colors ${
                    activeTab === 'retirement'
                      ? 'border-primary text-primary'
                      : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                  }`}
                >
                  Retirement Projection
                </button>
              </nav>
            </div>
          </Card>

          {/* Tab Content */}
          <Card tier="raised">
            {activeTab === 'overview' && renderOverview()}
            {activeTab === 'charts' && renderCharts()}
            {activeTab === 'retirement' && renderRetirement()}
          </Card>
        </div>
      </div>
      </Layout>
    </>
  );
}