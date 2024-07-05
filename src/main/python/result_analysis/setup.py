from setuptools import setup, find_packages
setup(
    name='result_analysis',
    version='0.1.0',
    packages=find_packages(),
    entry_points={
        'console_scripts': [
            'result_analysis = result_analysis.__main__:main'
        ]
    }
)
